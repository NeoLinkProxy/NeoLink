package neoproxy.neolink.core;

import fun.ceroxe.api.print.log.LogType;
import top.ceroxe.api.neolink.NeoLinkAPI;
import top.ceroxe.api.neolink.NeoLinkAPI.TransportProtocol;
import top.ceroxe.api.neolink.NeoLinkCfg;
import top.ceroxe.api.neolink.exception.NoMoreNetworkFlowException;
import top.ceroxe.api.neolink.exception.NoSuchKeyException;
import top.ceroxe.api.neolink.exception.UnsupportedVersionException;
import neoproxy.neolink.update.UpdateManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * Application-layer owner for the single NeoLinkAPI tunnel instance.
 *
 * <p>The desktop client is intentionally a shell: configuration, UI, logging,
 * node loading, and update orchestration stay here; all protocol lifecycle and
 * TCP/UDP forwarding belong to NeoLinkAPI. Keeping the only API instance in
 * this class avoids scattering tunnel ownership across GUI and CLI code.</p>
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
        Objects.requireNonNull(remoteDomain, "remoteDomain");
        Objects.requireNonNull(accessKey, "accessKey");
        debugOperation("Starting NeoLinkAPI tunnel. Remote: " + remoteDomain + ", Local: " + localPort);
        shouldStop = false;
        NeoLink.remoteDomainName = remoteDomain;
        NeoLink.localPort = localPort;
        NeoLink.key = accessKey;

        boolean firstRun = true;
        while (!shouldStop) {
            if (!firstRun) {
                waitBeforeReconnect();
                if (shouldStop) {
                    break;
                }
            }
            firstRun = false;

            NeoLinkAPI activeTunnel = buildTunnel(remoteDomain, localPort, accessKey);
            synchronized (LOCK) {
                if (shouldStop) {
                    activeTunnel.close();
                    break;
                }
                tunnel = activeTunnel;
            }

            try {
                activeTunnel.start();
                waitUntilStopped(activeTunnel);
            } catch (UnsupportedVersionException e) {
                NeoLink.say(e.serverResponse(), LogType.ERROR);
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
                NeoLink.say(e.serverResponse(), LogType.ERROR);
                stopAfterTerminalFailure();
            } catch (NoMoreNetworkFlowException e) {
                NeoLink.say(e.serverResponse(), LogType.ERROR);
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
                NeoLink.remotePort = 0;
            }
        }
        debugOperation("NeoLinkAPI tunnel runner exited.");
    }

    private static NeoLinkAPI buildTunnel(String remoteDomain, int localPort, String accessKey) {
        NeoLinkCfg cfg = new NeoLinkCfg(remoteDomain, NeoLink.hostHookPort, NeoLink.hostConnectPort, accessKey, localPort)
                .setLocalDomainName(NeoLink.localDomainName)
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

        return new NeoLinkAPI(cfg)
                .setUnsupportedVersionDecision(response -> NeoLink.enableAutoUpdate)
                .setOnRemotePortChanged(port -> NeoLink.remotePort = port)
                .setOnServerMessage(NeoLink::say)
                .setOnError((message, cause) -> {
                    if (message != null && !message.isBlank()) {
                        NeoLink.say(message, LogType.ERROR);
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

    private static void waitUntilStopped(NeoLinkAPI activeTunnel) {
        while (!shouldStop && activeTunnel.isActive()) {
            try {
                Thread.sleep(200);
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

    public interface StopCallback {
        void onStop();
    }
}
