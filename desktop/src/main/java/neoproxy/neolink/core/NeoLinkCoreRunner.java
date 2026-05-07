package neoproxy.neolink.core;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.cli.ClientConsole;
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
import java.util.concurrent.atomic.AtomicReference;

import static neoproxy.neolink.util.Debugger.debugOperation;
import neoproxy.neolink.state.ConnectionSettings;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureSettings;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

/**
 * NeoLink 隧道运行器（tunnel runtime coordinator）。
 *
 * <p>本类拥有单一 `NeoLinkAPI` 实例的生命周期控制权：启动、重连、停止、运行时协议切换都在
 * 这里串行化处理。UI / CLI 只负责触发，不直接持有 tunnel 所有权。</p>
 */
public final class NeoLinkCoreRunner {
    private static final Object LOCK = new Object();
    private static volatile boolean shouldStop = false;
    private static volatile NeoLinkAPI tunnel;
    private static volatile StopCallback stopCallback;
    private static final AtomicReference<TransportSelection> transportSelection =
            new AtomicReference<>(new TransportSelection(true, true));

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
        TransportSelection selection = new TransportSelection(tcpEnabled, udpEnabled);
        transportSelection.set(selection);
        FeatureState.applyRuntimeTransportSelection(tcpEnabled, udpEnabled);

        synchronized (LOCK) {
            NeoLinkAPI activeTunnel = tunnel;
            if (activeTunnel == null || !activeTunnel.isActive()) {
                debugOperation("Skipping runtime protocol switch because no active tunnel is running.");
                return;
            }

            debugOperation("Applying runtime protocol switch. tcpEnabled=" + tcpEnabled + ", udpEnabled=" + udpEnabled);
            activeTunnel.updateRuntimeProtocolFlags(tcpEnabled, udpEnabled);
        }
    }

    public static void updateRuntimePpv2(boolean ppv2Enabled) {
        FeatureState.setEnableProxyProtocol(ppv2Enabled);

        synchronized (LOCK) {
            NeoLinkAPI activeTunnel = tunnel;
            if (activeTunnel == null) {
                debugOperation("Skipping runtime PPv2 switch because no tunnel instance is available.");
                return;
            }

            // NeoLinkAPI 7.1.12 writes PPv2 to both cfg and runtimeCfg. Calling it while the
            // tunnel is still starting keeps the UI switch, the pending startup config, and
            // future TCP connection behavior aligned.
            debugOperation("Applying runtime PPv2 switch. ppv2Enabled=" + ppv2Enabled);
            activeTunnel.setPPV2Enabled(ppv2Enabled);
        }
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
        ConnectionSettings settings = ConnectionState.snapshot();
        runCore(new NeoLinkCfg(
                remoteDomain,
                settings.hostHookPort(),
                settings.hostConnectPort(),
                accessKey,
                localPort
        ));
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
        ConnectionSettings currentConnection = ConnectionState.snapshot();
        ConnectionState.apply(new ConnectionSettings(
                remoteDomain,
                currentConnection.localDomainName(),
                cfg.getHookPort(),
                cfg.getHostConnectPort(),
                accessKey,
                localPort,
                currentConnection.specifiedNodeName()
        ));
        FeatureSettings currentFeatures = FeatureState.snapshot();
        transportSelection.set(new TransportSelection(!currentFeatures.disableTcp(), !currentFeatures.disableUdp()));

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
            RuntimeState.setTunnelAddress(null);
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
                ClientConsole.say(clientFacingApiErrorMessage(e.serverResponse(), e), LogType.ERROR);
                if (FeatureState.snapshot().enableAutoUpdate()) {
                    UpdateManager.checkUpdate(
                            NeoLink.CLIENT_FILE_PREFIX + latestVersionFromServerResponse(e.serverResponse()),
                            activeTunnel.getUpdateURL()
                    );
                } else {
                    ClientConsole.say(languageData().PLEASE_UPDATE_MANUALLY, LogType.ERROR);
                }
                stopAfterTerminalFailure();
            } catch (NoSuchKeyException e) {
                ClientConsole.say(clientFacingApiErrorMessage(e.serverResponse(), e), LogType.ERROR);
                stopAfterTerminalFailure();
            } catch (NoMoreNetworkFlowException e) {
                ClientConsole.say(clientFacingApiErrorMessage(e.serverResponse(), e), LogType.ERROR);
                stopAfterTerminalFailure();
            } catch (PortOccupiedException | NoMorePortException e) {
                ClientConsole.say(clientFacingApiErrorMessage(null, e), LogType.ERROR);
                stopAfterTerminalFailure();
            } catch (IOException e) {
                debugOperation(e);
                if (!FeatureState.snapshot().enableAutoReconnect() && !shouldStop) {
                    ClientConsole.say(languageData().FAIL_TO_BUILD_A_CHANNEL_FROM + remoteDomain, LogType.ERROR);
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
                RuntimeState.setTunnelAddress(null);
            }
        }
        debugOperation("NeoLinkAPI tunnel runner exited.");
    }

    private static NeoLinkAPI buildTunnel(NeoLinkCfg cfg, AtomicBoolean tunnelReachedRunningState) {
        TransportSelection selection = transportSelection.get();
        ConnectionSettings connectionSettings = ConnectionState.snapshot();
        FeatureSettings featureSettings = FeatureState.snapshot();
        cfg.setLocalDomainName(connectionSettings.localDomainName())
                .setTCPEnabled(selection.tcpEnabled())
                .setUDPEnabled(selection.udpEnabled())
                .setPPV2Enabled(featureSettings.enableProxyProtocol())
                // 调试开关统一由 FeatureState -> NeoLinkAPI 全局 Debugger 同步，避免把一次启动时的布尔值
                // 固化进 runtimeCfg 后，GUI 运行时切换调试模式却无法正确关闭后续调试输出。
                .setDebugMsg(false)
                .setHeartBeatPacketDelay(featureSettings.heartbeatPacketDelay())
                .setProxyIPToNeoServer(featureSettings.proxyIPToNeoServer())
                .setProxyIPToLocalServer(featureSettings.proxyIPToLocalServer())
                .setClientVersion(ClientConsole.getClientVersionToReport());
        if (RuntimeState.languageData() != null) {
            cfg.setLanguage(RuntimeState.languageData().getCurrentLanguage());
        }

        NeoLinkAPI api = new NeoLinkAPI(cfg);
        return api.setUnsupportedVersionDecision(response -> FeatureState.snapshot().enableAutoUpdate())
                .setOnStateChanged(state -> {
                    if (state == NeoLinkState.RUNNING) {
                        tunnelReachedRunningState.set(true);
                        Thread.ofVirtual().name("NeoLink-tunnel-address-listener").start(
                                () -> publishTunnelAddress(api)
                        );
                    }
                })
                .setOnServerMessage(ClientConsole::say)
                .setOnError((message, cause) -> {
                    String displayMessage = clientFacingCallbackErrorMessage(
                            message,
                            cause,
                            tunnelReachedRunningState.get()
                    );
                    if (displayMessage != null && !displayMessage.isBlank()) {
                        ClientConsole.say(displayMessage, LogType.ERROR);
                    }
                    if (cause instanceof Exception exception) {
                        debugOperation(exception);
                    }
                })
                .setOnConnect(NeoLinkCoreRunner::logConnect)
                .setOnDisconnect(NeoLinkCoreRunner::logDisconnect)
                .setOnConnectNeoFailure(() -> ClientConsole.say(
                        languageData().FAIL_TO_BUILD_A_CHANNEL_FROM + ConnectionState.snapshot().remoteDomainName(),
                        LogType.ERROR
                ))
                .setOnConnectLocalFailure(() -> ClientConsole.say(
                        languageData().FAIL_TO_CONNECT_LOCALHOST + ConnectionState.snapshot().localPort(),
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
            RuntimeState.setTunnelAddress(activeTunnel.getTunAddr());
            debugOperation("NeoLink tunnel address received from NeoLinkAPI: " + RuntimeState.tunnelAddress());
        } catch (RuntimeException e) {
            debugOperation(e);
        }
    }

    private static void waitBeforeReconnect() {
        FeatureSettings features = FeatureState.snapshot();
        if (!features.enableAutoReconnect()) {
            shouldStop = true;
            return;
        }
        for (int i = 0; i < features.reconnectionIntervalSeconds() && !shouldStop; i++) {
            if (RuntimeState.languageData() != null) {
                ClientConsole.say(RuntimeState.languageData().getReconnectMessage(features.reconnectionIntervalSeconds() - i));
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
        logConnection(protocol, source, target, languageData().BUILD_UP);
    }

    private static void logDisconnect(TransportProtocol protocol, InetSocketAddress source, InetSocketAddress target) {
        logConnection(protocol, source, target, languageData().DESTROY);
    }

    private static void logConnection(
            TransportProtocol protocol,
            InetSocketAddress source,
            InetSocketAddress target,
            String suffix
    ) {
        if (FeatureState.snapshot().showConnection()) {
            ClientConsole.say(connectionLabel(protocol) + formatAddress(source) + " -> " + formatAddress(target) + suffix);
        }
    }

    private static String connectionLabel(TransportProtocol protocol) {
        return protocol == TransportProtocol.UDP
                ? languageData().A_UDP_CONNECTION
                : languageData().A_TCP_CONNECTION;
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
        if (RuntimeState.languageData() == null) {
            LanguageManager.detectLanguage();
        }
        return RuntimeState.languageData() != null ? RuntimeState.languageData() : new LanguageData();
    }

    public interface StopCallback {
        void onStop();
    }

    private record TransportSelection(boolean tcpEnabled, boolean udpEnabled) {
    }
}
