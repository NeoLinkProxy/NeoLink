package neoproxy.neolink.core;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.network.InternetOperator;
import neoproxy.neolink.network.ProxyOperator;
import neoproxy.neolink.network.threads.CheckAliveThread;
import neoproxy.neolink.util.Debugger;

import java.net.InetSocketAddress;
import java.net.Socket;

import static neoproxy.neolink.util.Debugger.debugOperation;
import static neoproxy.neolink.core.NeoLink.enableAutoReconnect;

/**
 * NeoLink 核心运行器（GUI 模式）
 *
 * 核心职责：
 * 1. 在 GUI 模式下管理 NeoLink 核心服务的生命周期
 * 2. 处理连接、认证、命令监听等核心流程
 * 3. 支持优雅停止和错误处理
 * 4. 实现自动重连机制
 *
 * 设计特点：
 * - 与 GUI 解耦，通过回调接口通信
 * - 支持外部停止请求
 * - 自动重连逻辑
 * - 详细的调试日志
 *
 * 与 NeoLink.main() 的区别：
 * - NeoLink.main() 用于 CLI 模式，包含交互式输入
 * - NeoLinkCoreRunner 用于 GUI 模式，参数由界面传入
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
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