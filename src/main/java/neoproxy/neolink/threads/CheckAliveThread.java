package neoproxy.neolink.threads;

import neoproxy.neolink.NeoLink;
import plethora.utils.Sleeper;

import java.util.concurrent.atomic.AtomicBoolean;

import static neoproxy.neolink.InternetOperator.close;
import static neoproxy.neolink.NeoLink.debugOperation;
import static neoproxy.neolink.NeoLink.isDebugMode;

/**
 * 客户端心跳发送线程 (单例工厂版).
 * <p>
 * 优化点：
 * 1. 【单例模式】确保全局只有一个心跳线程实例，避免重复创建和管理。
 * 2. 【工厂方法】提供静态的 {@code startThread()} 和 {@code stopThread()} 方法，
 *    作为全局唯一的入口，简化外部调用。
 * 3. 【线程安全】使用“双重检查锁定”模式实现线程安全的懒加载单例。
 * 4. 【职责分离】线程的职责是“检测心跳失败”，并通过关闭底层 Socket 来“通知”主循环。
 * </p>
 */
public final class CheckAliveThread implements Runnable {

    private static final String HEARTBEAT_PACKET = "PING";
    public static int HEARTBEAT_PACKET_DELAY = 1000; // 默认1秒

    // --- 单例实现 ---
    private static volatile CheckAliveThread instance; // volatile 保证多线程下的可见性

    // --- 实例字段 ---
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread heartbeatThreadInstance;

    /**
     * 私有构造函数，防止外部实例化。
     */
    private CheckAliveThread() {
    }

    /**
     * 【工厂方法】获取 CheckAliveThread 的单例实例。
     * 使用双重检查锁定模式，确保线程安全和高效的懒加载。
     *
     * @return 全局唯一的 CheckAliveThread 实例。
     */
    private static CheckAliveThread getInstance() {
        if (instance == null) {
            synchronized (CheckAliveThread.class) {
                if (instance == null) {
                    instance = new CheckAliveThread();
                }
            }
        }
        return instance;
    }

    /**
     * 【工厂方法】启动全局唯一的心跳线程。
     * 如果线程已经启动，则不会重复启动。
     */
    public static void startThread() {
        getInstance().start();
    }

    /**
     * 【工厂方法】停止全局唯一的心跳线程。
     * 此方法是线程安全的。
     */
    public static void stopThread() {
        if (instance != null) {
            instance.stop();
        }
    }

    // --- 以下为实例方法，负责实际的线程控制 ---

    /**
     * 启动心跳线程。
     * @return 启动的线程实例，便于外部管理（如中断）。
     */
    private Thread start() {
        if (isRunning.compareAndSet(false, true)) {
            heartbeatThreadInstance = new Thread(this, "Client-CheckAliveThread");
            heartbeatThreadInstance.setDaemon(true); // 设置为守护线程，主程序退出时自动结束
            heartbeatThreadInstance.start();
            if (isDebugMode) {
                System.out.println("CheckAliveThread started.");
            }
            return heartbeatThreadInstance;
        } else {
            if (isDebugMode) {
                System.err.println("CheckAliveThread is already running.");
            }
            return heartbeatThreadInstance;
        }
    }

    /**
     * 停止心跳线程。
     * 此方法是线程安全的。
     */
    private void stop() {
        if (isRunning.compareAndSet(true, false)) {
            if (isDebugMode) {
                System.out.println("Stopping CheckAliveThread...");
            }
            if (heartbeatThreadInstance != null) {
                heartbeatThreadInstance.interrupt();
            }
        }
    }

    @Override
    public void run() {
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 1. 发送心跳包
                NeoLink.hookSocket.sendStr(HEARTBEAT_PACKET);
            } catch (Exception e) {
                // 2. 发送失败，通常意味着连接已断开
                if (isDebugMode) {
                    System.err.println("Failed to send heartbeat. Connection to server is lost. Notifying main loop...");
                }
                debugOperation(e);

                // 3. 【关键优化】只负责关闭连接，通知主循环。
                close(NeoLink.hookSocket);

                // 4. 停止自身
                stop();
                break; // 退出 while 循环
            }

            // 5. 等待下一次发送，同时响应中断
            Sleeper.sleep(HEARTBEAT_PACKET_DELAY);
        }
        if (isDebugMode) {
            System.out.println("CheckAliveThread finished.");
        }
    }
}