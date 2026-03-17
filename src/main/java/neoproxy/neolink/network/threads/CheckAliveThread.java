package neoproxy.neolink.network.threads;

import fun.ceroxe.api.utils.Sleeper;
import neoproxy.neolink.core.NeoLink;
import neoproxy.neolink.network.InternetOperator;
import neoproxy.neolink.util.Debugger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static neoproxy.neolink.util.Debugger.debugOperation;
import static neoproxy.neolink.network.InternetOperator.close;
import static neoproxy.neolink.core.NeoLink.isDebugMode;

/**
 * 心跳检测线程
 *
 * 核心职责：
 * 1. 定期向服务器发送心跳包，保持连接活跃
 * 2. 检测连接状态，识别连接中断
 * 3. 连续失败达到阈值时自动关闭连接，触发重连机制
 *
 * 设计特点：
 * - 单例模式，确保只有一个心跳线程运行
 * - 使用原子变量保证线程安全
 * - 守护线程，不阻止 JVM 退出
 * - 可安全启动和停止
 *
 * 心跳机制：
 * - 每 HEARTBEAT_PACKET_DELAY 毫秒检测一次
 * - 如果超过 2 秒未收到服务器消息，发送 PING 心跳
 * - 连续 MAX_CONSECUTIVE_FAILURES 次失败则判定连接断开
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
public final class CheckAliveThread implements Runnable {

    private static final String HEARTBEAT_PACKET = "PING";
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    public static int HEARTBEAT_PACKET_DELAY = 1000;
    private static volatile CheckAliveThread instance;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread heartbeatThreadInstance;

    private CheckAliveThread() {
    }

    private static CheckAliveThread getInstance() {
        if (instance == null) {
            synchronized (CheckAliveThread.class) {
                if (instance == null) instance = new CheckAliveThread();
            }
        }
        return instance;
    }

    public static void startThread() {
        getInstance().start();
    }

    public static void stopThread() {
        if (instance != null) instance.stop();
    }

    private Thread start() {
        if (isRunning.compareAndSet(false, true)) {
            heartbeatThreadInstance = new Thread(this, "Client-CheckAliveThread");
            heartbeatThreadInstance.setDaemon(true);
            heartbeatThreadInstance.start();
            debugOperation("[DEBUG] CheckAliveThread started.");
            return heartbeatThreadInstance;
        } else {
            return heartbeatThreadInstance;
        }
    }

    private void stop() {
        if (isRunning.compareAndSet(true, false)) {
            debugOperation("[DEBUG] Stopping CheckAliveThread...");
            if (heartbeatThreadInstance != null) heartbeatThreadInstance.interrupt();
        }
    }

    @Override
    public void run() {
        AtomicInteger failureCount = new AtomicInteger(0);
        debugOperation("CheckAliveThread loop started.");

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {

            long timeSinceLastRecv = System.currentTimeMillis() - NeoLink.lastReceivedTime;

            if (timeSinceLastRecv > 2000) {
                try {
                    synchronized (NeoLink.hookSocket) {
                        // debugOperation("Sending Heartbeat PING..."); // Optional: Uncomment if needed, but might spam
                        NeoLink.hookSocket.sendStr(HEARTBEAT_PACKET);
                    }
                    failureCount.set(0);

                } catch (Exception e) {
                    int currentFailures = failureCount.incrementAndGet();
                    if (isDebugMode)
                        System.err.println("[DEBUG] Heartbeat failed (" + currentFailures + "): " + e.getMessage());

                    if (currentFailures >= MAX_CONSECUTIVE_FAILURES) {
                        debugOperation(e);
                        debugOperation("Max heartbeat failures reached. Closing socket.");
                        close(NeoLink.hookSocket);
                        stop();
                        break;
                    }
                }
            } else {
                failureCount.set(0);
            }

            Sleeper.sleep(HEARTBEAT_PACKET_DELAY);
        }
        debugOperation("CheckAliveThread finished.");
    }
}