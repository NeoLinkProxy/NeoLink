package neoproxy.neolink.gui;

import javafx.application.Platform;
import neoproxy.neolink.InternetOperator;
import neoproxy.neolink.NeoLink;
import neoproxy.neolink.ProxyOperator;
import neoproxy.neolink.threads.CheckAliveThread;
import plethora.net.SecureSocket;

import java.net.InetSocketAddress;
import java.net.Socket;

import static neoproxy.neolink.Debugger.debugOperation;
import static neoproxy.neolink.NeoLink.enableAutoReconnect;

public class NeoLinkCoreRunner {
    private static volatile boolean shouldStop = false;

    public static void requestStop() {
        debugOperation("Requesting CoreRunner stop...");
        shouldStop = true;
    }

    public static void runCore(String remoteDomain, int localPort, String accessKey) {
        debugOperation("CoreRunner started. Remote: " + remoteDomain + ", Local: " + localPort);
        shouldStop = false;
        NeoLink.remoteDomainName = remoteDomain;
        NeoLink.localPort = localPort;
        NeoLink.key = accessKey;
        ProxyOperator.init();
        boolean firstRun = true;

        while (!shouldStop) {
            SecureSocket hookSocket = null;
            Socket rawSocket = null;

            try {
                if (!firstRun) {
                    debugOperation("Entering reconnection wait loop...");
                    for (int i = 0; i < NeoLink.reconnectionIntervalSeconds && !shouldStop; i++) {
                        NeoLink.languageData.sayReconnectMsg(NeoLink.reconnectionIntervalSeconds - i);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            shouldStop = true;
                            debugOperation("Reconnection loop interrupted.");
                            break;
                        }
                    }
                    if (shouldStop) break;
                }
                firstRun = false;
                NeoLink.say(NeoLink.languageData.CONNECT_TO + remoteDomain + NeoLink.languageData.OMITTED);

                if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
                    debugOperation("Connecting via ProxyOperator...");
                    hookSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, NeoLink.hostHookPort);
                } else {
                    debugOperation("Initiating direct connection...");
                    rawSocket = new Socket();
                    NeoLink.connectingSocket = rawSocket;

                    debugOperation("Socket connecting (timeout 10s)...");
                    rawSocket.connect(new InetSocketAddress(remoteDomain, NeoLink.hostHookPort), 10000);
                    debugOperation("Raw socket connected.");

                    debugOperation("Upgrading to SecureSocket...");
                    hookSocket = new SecureSocket(rawSocket);
                }

                NeoLink.connectingSocket = null;
                NeoLink.hookSocket = hookSocket;
                debugOperation("Socket established and secured.");

                NeoLink.exchangeClientInfoWithServer();
                CheckAliveThread.startThread();
                NeoLink.listenForServerCommands();

            } catch (Exception e) {
                if (!enableAutoReconnect && !shouldStop) {
                    Platform.runLater(() -> {
                        if (NeoLink.mainWindowController != null) {
                            NeoLink.mainWindowController.stopService();
                        }
                    });
                }

                if (!shouldStop) {
                    debugOperation("Core loop exception caught.");
                    debugOperation(e);
                } else {
                    debugOperation("Core loop exception ignored due to stop request.");
                }
            } finally {
                try {
                    if (NeoLink.connectingSocket != null) {
                        debugOperation("Cleaning up pending connecting socket.");
                        NeoLink.connectingSocket.close();
                        NeoLink.connectingSocket = null;
                    }

                    InternetOperator.close(hookSocket);
                    CheckAliveThread.stopThread();
                    NeoLink.hookSocket = null;
                    NeoLink.remotePort = 0;
                    debugOperation("Core runner iteration cleanup finished.");
                } catch (Exception ignored) {
                }
            }
        }
        debugOperation("CoreRunner exited main loop.");
    }
}