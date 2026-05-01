package neoproxy.neolink.core;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.update.UpdateManager;
import top.ceroxe.api.neolink.NeoLinkAPI;
import top.ceroxe.api.neolink.NeoLinkAPI.TransportProtocol;
import top.ceroxe.api.neolink.NeoLinkCfg;
import top.ceroxe.api.neolink.NeoLinkState;
import top.ceroxe.api.neolink.exception.NoMoreNetworkFlowException;
import top.ceroxe.api.neolink.exception.NoMorePortException;
import top.ceroxe.api.neolink.exception.NoSuchKeyException;
import top.ceroxe.api.neolink.exception.OutDatedKeyException;
import top.ceroxe.api.neolink.exception.PortOccupiedException;
import top.ceroxe.api.neolink.exception.UnRecognizedKeyException;
import top.ceroxe.api.neolink.exception.UnSupportHostVersionException;
import top.ceroxe.api.neolink.exception.UnsupportedVersionException;
import top.ceroxe.api.print.log.LogType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * 单个 NeoLinkAPI 隧道实例的应用层所有者。
 *
 * <p>桌面客户端有意保持为壳层：配置、UI、日志、节点加载和更新编排留在这里；
 * 协议生命周期与 TCP/UDP 转发全部交给 NeoLinkAPI。把唯一 API 实例收敛到本类，
 * 可以避免 GUI 和 CLI 代码分散持有隧道所有权。</p>
 */
public final class NeoLinkCoreRunner {
    private static final Object LOCK = new Object();
    private static volatile boolean shouldStop = false;
    private static volatile NeoLinkAPI tunnel;
    private static StopCallback stopCallback;

    private NeoLinkCoreRunner() {
    }

    public static void setStopCallback(StopCallback callback) {
        stopCallback = callback;
    }

    public static NeoLinkAPI currentTunnel() {
        return tunnel;
    }

    public static boolean isRunning() {
        NeoLinkAPI activeTunnel = tunnel;
        return activeTunnel != null && activeTunnel.isActive();
    }

    public static void updateRuntimeProtocolFlags(boolean tcpEnabled, boolean udpEnabled) throws IOException {
        NeoLink.isDisableTCP = !tcpEnabled;
        NeoLink.isDisableUDP = !udpEnabled;

        NeoLinkAPI activeTunnel = currentTunnel();
        if (activeTunnel == null || !activeTunnel.isActive()) {
            debugOperation("Skipping runtime protocol switch because no active tunnel is running.");
            return;
        }

        debugOperation("Applying runtime protocol switch. tcpEnabled=" + tcpEnabled + ", udpEnabled=" + udpEnabled);
        activeTunnel.updateRuntimeProtocolFlags(tcpEnabled, udpEnabled);
    }

    public static void requestStop() {
        debugOperation("Requesting NeoLinkAPI tunnel stop...");
        shouldStop = true;
        NeoLinkAPI activeTunnel;
        synchronized (LOCK) {
            activeTunnel = tunnel;
            tunnel = null;
        }
        if (activeTunnel != null) {
            activeTunnel.close();
        }
    }

    public static void runCore(String remoteDomain, int localPort, String accessKey) {
        runCore(new NeoLinkCfg(remoteDomain, NeoLink.hostHookPort, NeoLink.hostConnectPort, accessKey, localPort));
    }

    public static void runCore(NeoLinkCfg cfg) {
        Objects.requireNonNull(cfg, "cfg");
        String remoteDomain = cfg.getRemoteDomainName();
        int localPort = cfg.getLocalPort();
        String accessKey = cfg.getKey();
        Objects.requireNonNull(remoteDomain, "remoteDomain");
        Objects.requireNonNull(accessKey, "accessKey");
        debugOperation("Starting NeoLinkAPI tunnel. Remote: " + remoteDomain + ", Local: " + localPort);
        shouldStop = false;
        NeoLink.remoteDomainName = remoteDomain;
        NeoLink.localPort = localPort;
        NeoLink.key = accessKey;
        NeoLink.hostHookPort = cfg.getHookPort();
        NeoLink.hostConnectPort = cfg.getHostConnectPort();

        boolean firstRun = true;
        while (!shouldStop) {
            if (!firstRun) {
                waitBeforeReconnect();
                if (shouldStop) {
                    break;
                }
            }
            firstRun = false;

            AtomicBoolean tunnelReachedRunningState = new AtomicBoolean(false);
            NeoLinkAPI activeTunnel = buildTunnel(cfg, tunnelReachedRunningState);
            NeoLink.tunnelAddress = null;
            synchronized (LOCK) {
                if (shouldStop) {
                    activeTunnel.close();
                    break;
                }
                tunnel = activeTunnel;
            }

            try {
                activeTunnel.start();
            } catch (UnsupportedVersionException e) {
                NeoLink.say(clientFacingApiErrorMessage(e.serverResponse(), e), LogType.ERROR);
                if (NeoLink.enableAutoUpdate) {
                    UpdateManager.checkUpdate(
                            NeoLink.CLIENT_FILE_PREFIX + latestVersionFromServerResponse(e.serverResponse()),
                            activeTunnel.getUpdateURL()
                    );
                } else {
                    NeoLink.say(NeoLink.languageData.PLEASE_UPDATE_MANUALLY, LogType.ERROR);
                }
                stopAfterTerminalFailure();
            } catch (NoSuchKeyException e) {
                NeoLink.say(clientFacingApiErrorMessage(e.serverResponse(), e), LogType.ERROR);
                stopAfterTerminalFailure();
            } catch (NoMoreNetworkFlowException e) {
                NeoLink.say(clientFacingApiErrorMessage(e.serverResponse(), e), LogType.ERROR);
                stopAfterTerminalFailure();
            } catch (PortOccupiedException | NoMorePortException e) {
                NeoLink.say(clientFacingApiErrorMessage(null, e), LogType.ERROR);
                stopAfterTerminalFailure();
            } catch (IOException e) {
                debugOperation(e);
                if (!NeoLink.enableAutoReconnect && !shouldStop) {
                    NeoLink.say(NeoLink.languageData.FAIL_TO_BUILD_A_CHANNEL_FROM + remoteDomain, LogType.ERROR);
                    stopAfterTerminalFailure();
                }
            } catch (RuntimeException e) {
                debugOperation(e);
                stopAfterTerminalFailure();
            } finally {
                activeTunnel.close();
                synchronized (LOCK) {
                    if (tunnel == activeTunnel) {
                        tunnel = null;
                    }
                }
                NeoLink.tunnelAddress = null;
            }
        }
        debugOperation("NeoLinkAPI tunnel runner exited.");
    }

    private static NeoLinkAPI buildTunnel(NeoLinkCfg cfg, AtomicBoolean tunnelReachedRunningState) {
        cfg.setLocalDomainName(NeoLink.localDomainName)
                .setTCPEnabled(!NeoLink.isDisableTCP)
                .setUDPEnabled(!NeoLink.isDisableUDP)
                .setPPV2Enabled(NeoLink.enableProxyProtocol)
                .setDebugMsg(NeoLink.isDebugMode)
                .setHeartBeatPacketDelay(NeoLink.heartbeatPacketDelay)
                .setProxyIPToNeoServer(NeoLink.proxyIPToNeoServer)
                .setProxyIPToLocalServer(NeoLink.proxyIPToLocalServer)
                .setClientVersion(NeoLink.getClientVersionToReport());
        if (NeoLink.languageData != null) {
            cfg.setLanguage(NeoLink.languageData.getCurrentLanguage());
        }

        NeoLinkAPI api = new NeoLinkAPI(cfg);
        return api.setUnsupportedVersionDecision(response -> NeoLink.enableAutoUpdate)
                .setOnStateChanged(state -> {
                    if (state == NeoLinkState.RUNNING) {
                        tunnelReachedRunningState.set(true);
                        Thread.ofVirtual().name("NeoLink-tunnel-address-listener").start(
                                () -> publishTunnelAddress(api)
                        );
                    }
                })
                .setOnServerMessage(NeoLink::say)
                .setOnError((message, cause) -> {
                    String displayMessage = clientFacingCallbackErrorMessage(
                            message,
                            cause,
                            tunnelReachedRunningState.get()
                    );
                    if (displayMessage != null && !displayMessage.isBlank()) {
                        NeoLink.say(displayMessage, LogType.ERROR);
                    }
                    if (cause instanceof Exception exception) {
                        debugOperation(exception);
                    }
                })
                .setOnConnect(NeoLinkCoreRunner::logConnect)
                .setOnDisconnect(NeoLinkCoreRunner::logDisconnect)
                .setOnConnectNeoFailure(() -> NeoLink.say(
                        NeoLink.languageData.FAIL_TO_BUILD_A_CHANNEL_FROM + NeoLink.remoteDomainName,
                        LogType.ERROR
                ))
                .setOnConnectLocalFailure(() -> NeoLink.say(
                        NeoLink.languageData.FAIL_TO_CONNECT_LOCALHOST + NeoLink.localPort,
                        LogType.ERROR
                ))
                .setDebugSink((message, cause) -> {
                    if (message != null) {
                        debugOperation(message);
                    }
                    if (cause instanceof Exception exception) {
                        debugOperation(exception);
                    }
                });
    }

    private static void publishTunnelAddress(NeoLinkAPI activeTunnel) {
        try {
            NeoLink.tunnelAddress = activeTunnel.getTunAddr();
            debugOperation("NeoLink tunnel address received from NeoLinkAPI: " + NeoLink.tunnelAddress);
        } catch (RuntimeException e) {
            debugOperation(e);
        }
    }

    private static void waitBeforeReconnect() {
        if (!NeoLink.enableAutoReconnect) {
            shouldStop = true;
            return;
        }
        for (int i = 0; i < NeoLink.reconnectionIntervalSeconds && !shouldStop; i++) {
            if (NeoLink.languageData != null) {
                NeoLink.languageData.sayReconnectMsg(NeoLink.reconnectionIntervalSeconds - i);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                shouldStop = true;
                break;
            }
        }
    }

    private static void stopAfterTerminalFailure() {
        shouldStop = true;
        StopCallback callback = stopCallback;
        if (callback != null) {
            callback.onStop();
        }
    }

    private static String latestVersionFromServerResponse(String serverResponse) {
        if (serverResponse == null || serverResponse.isBlank()) {
            return VersionInfo.VERSION;
        }
        int separatorIndex = serverResponse.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == serverResponse.length() - 1) {
            return VersionInfo.VERSION;
        }
        String versions = serverResponse.substring(separatorIndex + 1);
        String[] parts = versions.split("\\|");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return VersionInfo.VERSION;
    }

    private static void logConnect(TransportProtocol protocol, InetSocketAddress source, InetSocketAddress target) {
        logConnection(protocol, source, target, NeoLink.languageData.BUILD_UP);
    }

    private static void logDisconnect(TransportProtocol protocol, InetSocketAddress source, InetSocketAddress target) {
        logConnection(protocol, source, target, NeoLink.languageData.DESTROY);
    }

    private static void logConnection(
            TransportProtocol protocol,
            InetSocketAddress source,
            InetSocketAddress target,
            String suffix
    ) {
        if (NeoLink.showConnection) {
            NeoLink.say(connectionLabel(protocol) + formatAddress(source) + " -> " + formatAddress(target) + suffix);
        }
    }

    private static String connectionLabel(TransportProtocol protocol) {
        return protocol == TransportProtocol.UDP
                ? NeoLink.languageData.A_UDP_CONNECTION
                : NeoLink.languageData.A_TCP_CONNECTION;
    }

    private static String formatAddress(InetSocketAddress address) {
        if (address == null) {
            return "unknown";
        }
        return address.getHostString() + ":" + address.getPort();
    }

    static String clientFacingApiErrorMessage(String message, Throwable cause) {
        LanguageData languageData = languageData();
        if (cause instanceof UnSupportHostVersionException unsupportedVersionException) {
            return firstNonBlank(unsupportedVersionException.serverResponse(), languageData.PLEASE_UPDATE_MANUALLY);
        }
        if (cause instanceof UnsupportedVersionException unsupportedVersionException) {
            return firstNonBlank(unsupportedVersionException.serverResponse(), languageData.PLEASE_UPDATE_MANUALLY);
        }
        if (cause instanceof OutDatedKeyException outDatedKeyException) {
            return firstNonBlank(outDatedKeyException.serverResponse(), languageData.PLEASE_UPDATE_MANUALLY);
        }
        if (cause instanceof UnRecognizedKeyException unRecognizedKeyException) {
            return firstNonBlank(unRecognizedKeyException.serverResponse(), message);
        }
        if (cause instanceof NoSuchKeyException noSuchKeyException) {
            return firstNonBlank(noSuchKeyException.serverResponse(), message);
        }
        if (cause instanceof NoMoreNetworkFlowException noMoreNetworkFlowException) {
            return firstNonBlank(normalizeNoFlowResponse(noMoreNetworkFlowException.serverResponse()), languageData.NO_FLOW_LEFT);
        }
        if (cause instanceof PortOccupiedException portOccupiedException) {
            return firstNonBlank(portOccupiedException.serverResponse(), message);
        }
        if (cause instanceof NoMorePortException noMorePortException) {
            return firstNonBlank(noMorePortException.serverResponse(), message);
        }
        return message;
    }

    static String clientFacingCallbackErrorMessage(String message, Throwable cause, boolean tunnelReachedRunningState) {
        if (!tunnelReachedRunningState) {
            return null;
        }
        if (isTerminalApiException(cause)) {
            return clientFacingApiErrorMessage(message, cause);
        }
        return null;
    }

    private static boolean isTerminalApiException(Throwable cause) {
        return cause instanceof UnsupportedVersionException
                || cause instanceof NoSuchKeyException
                || cause instanceof NoMoreNetworkFlowException
                || cause instanceof PortOccupiedException
                || cause instanceof NoMorePortException;
    }

    private static String normalizeNoFlowResponse(String serverResponse) {
        return serverResponse != null && serverResponse.startsWith("NeoProxyServer reported")
                ? null
                : serverResponse;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static LanguageData languageData() {
        if (NeoLink.languageData == null) {
            NeoLink.detectLanguage();
        }
        return NeoLink.languageData != null ? NeoLink.languageData : new LanguageData();
    }

    public interface StopCallback {
        void onStop();
    }
}
