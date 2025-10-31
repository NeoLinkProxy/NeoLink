package neoproject.neolink.gui;

import neoproject.neolink.*;
import neoproject.neolink.threads.CheckAliveThread;
import plethora.net.SecureSocket;

import static neoproject.neolink.NeoLink.debugOperation;
import static neoproject.neolink.NeoLink.enableAutoReconnect;

/**
 * NeoLink 核心逻辑运行器 (支持可中断的自动重连)。
 */
public class NeoLinkCoreRunner {
    private static volatile boolean shouldStop = false;

    public static void requestStop() {
        shouldStop = true;
    }

    public static void runCore(String remoteDomain, int localPort, String accessKey) {
        shouldStop = false;
        ConfigOperator.readAndSetValue();
        NeoLink.remoteDomainName = remoteDomain;
        NeoLink.localPort = localPort;
        NeoLink.key = accessKey;
        ProxyOperator.init();
        boolean firstRun = true;
        while (!shouldStop) {
            SecureSocket hookSocket = null;
            try {
                if (!firstRun) {
                    for (int i = 0; i < NeoLink.reconnectionIntervalSeconds && !shouldStop; i++) {
                        NeoLink.languageData.sayReconnectMsg(NeoLink.reconnectionIntervalSeconds - i);
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
                NeoLink.say(NeoLink.languageData.CONNECT_TO + remoteDomain + NeoLink.languageData.OMITTED);
                if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
                    hookSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, NeoLink.hostHookPort);
                } else {
                    hookSocket = new SecureSocket(remoteDomain, NeoLink.hostHookPort);
                }
                NeoLink.hookSocket = hookSocket;
                NeoLink.exchangeClientInfoWithServer();
                CheckAliveThread.startThread();
                NeoLink.listenForServerCommands();
            } catch (Exception e) {
                if (!enableAutoReconnect){
                    NeoLink.mainWindowController.stopService();
                }
                debugOperation(e);
                // 【关键修复】仅在非用户主动停止时才输出错误
//                if (!shouldStop) {
//                    NeoLink.say("ERROR: " + e.getMessage(), LogType.ERROR);
//                }
                // 如果是用户停止，静默退出
            } finally {
                try {
                    InternetOperator.close(hookSocket);
                    CheckAliveThread.stopThread();
                    NeoLink.hookSocket = null;
                    NeoLink.remotePort = 0;
                } catch (Exception ignored) {
                }
            }
        }
    }
}