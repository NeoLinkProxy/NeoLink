package neoproxy.neolink.threads;

import neoproxy.neolink.NeoLink;
import plethora.utils.Sleeper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static neoproxy.neolink.InternetOperator.close;
import static neoproxy.neolink.NeoLink.debugOperation;
import static neoproxy.neolink.NeoLink.isDebugMode;

/**
 * 客户端心跳发送线程 (被动式心跳 + 线程安全同步).
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
            if (isDebugMode) System.out.println("CheckAliveThread started.");
            return heartbeatThreadInstance;
        } else {
            return heartbeatThreadInstance;
        }
    }

    private void stop() {
        if (isRunning.compareAndSet(true, false)) {
            if (isDebugMode) System.out.println("Stopping CheckAliveThread...");
            if (heartbeatThreadInstance != null) heartbeatThreadInstance.interrupt();
        }
    }

    @Override
    public void run() {
        AtomicInteger failureCount = new AtomicInteger(0);

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {

            // 【核心优化 - 被动式心跳】
            // 只有当超过 2秒 没有收到服务器消息时，才发送心跳。
            // 压测期间，NeoLink.lastReceivedTime 会一直更新，所以这里不会进入发送逻辑。
            // 彻底避免了在最忙的时候去干扰 Socket。
            long timeSinceLastRecv = System.currentTimeMillis() - NeoLink.lastReceivedTime;

            if (timeSinceLastRecv > 2000) {
                try {
                    // 【关键修复】同步锁！
                    // 确保即使在空闲时发送心跳，也不会和刚刚到来的数据发生并发写入/读取冲突
                    synchronized (NeoLink.hookSocket) {
                        NeoLink.hookSocket.sendStr(HEARTBEAT_PACKET);
                    }
                    failureCount.set(0);

                } catch (Exception e) {
                    int currentFailures = failureCount.incrementAndGet();
                    if (isDebugMode)
                        System.err.println("Heartbeat failed (" + currentFailures + "): " + e.getMessage());

                    if (currentFailures >= MAX_CONSECUTIVE_FAILURES) {
                        debugOperation(e);
                        // 关闭 Socket 会触发主线程 receiveStr 抛异常，从而进入重连流程
                        close(NeoLink.hookSocket);
                        stop();
                        break;
                    }
                }
            } else {
                // 链路正忙（有数据流动），视为连接健康，重置失败计数
                failureCount.set(0);
            }

            Sleeper.sleep(HEARTBEAT_PACKET_DELAY);
        }
        if (isDebugMode) {
            System.out.println("CheckAliveThread finished.");
        }
    }
}