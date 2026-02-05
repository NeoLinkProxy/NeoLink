package neoproxy.neolink.gui;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.InternetOperator;
import neoproxy.neolink.NeoLink;
import neoproxy.neolink.ProxyOperator;
import neoproxy.neolink.threads.CheckAliveThread;

import java.net.InetSocketAddress;
import java.net.Socket;

import static neoproxy.neolink.Debugger.debugOperation;
import static neoproxy.neolink.NeoLink.enableAutoReconnect;

public class NeoLinkCoreRunner {
    private static volatile boolean shouldStop = false;
    private static StopCallback stopCallback;

    public static void setStopCallback(StopCallback callback) {
        stopCallback = callback;
    }

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
                    if (shouldStop) break;
                }
                firstRun = false;
                if (NeoLink.languageData != null) {
                    NeoLink.say(NeoLink.languageData.CONNECT_TO + remoteDomain + NeoLink.languageData.OMITTED);
                }

                if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
                    hookSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, NeoLink.hostHookPort);
                } else {
                    rawSocket = new Socket();
                    NeoLink.connectingSocket = rawSocket;
                    rawSocket.connect(new InetSocketAddress(remoteDomain, NeoLink.hostHookPort), 10000);
                    hookSocket = new SecureSocket(rawSocket);
                }

                NeoLink.connectingSocket = null;
                NeoLink.hookSocket = hookSocket;

                NeoLink.exchangeClientInfoWithServer();
                CheckAliveThread.startThread();
                NeoLink.listenForServerCommands();

            } catch (Exception e) {
                if (!enableAutoReconnect && !shouldStop) {
                    // 通知 UI 停止
                    if (stopCallback != null) {
                        stopCallback.onStop();
                    }
                    // 标记为停止，跳出循环
                    shouldStop = true;
                }

                if (!shouldStop) {
                    debugOperation("Core loop exception caught.");
                    debugOperation(e);
                }
            } finally {
                try {
                    if (NeoLink.connectingSocket != null) {
                        NeoLink.connectingSocket.close();
                        NeoLink.connectingSocket = null;
                    }
                    InternetOperator.close(hookSocket);
                    CheckAliveThread.stopThread();
                    NeoLink.hookSocket = null;
                    NeoLink.remotePort = 0;
                } catch (Exception ignored) {
                }
            }
        }
        debugOperation("CoreRunner exited main loop.");
    }

    // 添加一个回调接口，用于通知 UI 线程状态变化
    public interface StopCallback {
        void onStop();
    }
}