package neoproxy.neolink.threads;

import neoproxy.neolink.NeoLink;
import plethora.utils.Sleeper;

import static neoproxy.neolink.InternetOperator.close;
import static neoproxy.neolink.NeoLink.debugOperation;
import static neoproxy.neolink.NeoLink.isDebugMode;

/**
 * 客户端心跳发送线程。
 * <p>
 * 该线程的唯一职责是周期性地通过 {@code NeoLink.hookSocket} 向服务端发送 "PING" 心跳包，
 * 以维持连接的活跃状态。如果发送失败，则认为连接已中断，并触发线程停止和资源清理。
 * </p>
 *
 * <p>
 * <b>工业级实践特性：</b>
 * <ul>
 *   <li>协议明确：使用常量定义心跳包内容，避免硬编码和拼写错误。</li>
 *   <li>可配置性：心跳发送间隔可通过静态变量 {@code HEARTBEAT_PACKET_DELAY} 配置。</li>
 *   <li>健壮的异常处理：捕获所有发送异常，并触发优雅关闭流程。</li>
 *   <li>响应中断：正确处理 {@link InterruptedException}，允许线程被外部优雅地中断。</li>
 *   <li>线程命名：为线程设置有意义的名称，便于调试和监控。</li>
 *   <li>状态管理：使用 {@code isRunning} 标志位安全地控制线程的生命周期。</li>
 * </ul>
 * </p>
 */
public class CheckAliveThread implements Runnable {

    /**
     * 心跳包内容，必须与服务端期望的协议一致。
     */
    private static final String HEARTBEAT_PACKET = "PING";

    /**
     * 心跳包发送间隔（毫秒）。
     * 此值应为可配置项，建议根据网络环境和业务需求进行调整。
     */
    public static int HEARTBEAT_PACKET_DELAY = 1000; // 默认1秒

    /**
     * 线程运行状态标志。
     * 使用 volatile 确保多线程环境下的可见性。
     */
    private static volatile boolean isRunning = false;

    /**
     * 持有当前运行线程的引用，以便于管理和中断。
     */
    private static Thread heartbeatThreadInstance;

    private CheckAliveThread() {
        // 私有构造方法，防止外部直接实例化，强制通过 startThread() 启动
    }

    /**
     * 启动心跳线程。
     * 如果线程已在运行，则不会重复启动。
     */
    public static synchronized void startThread() {
        if (isRunning) {
            debugOperation(new Exception("CheckAliveThread is already started !"));
            return;
        }
        isRunning = true;
        heartbeatThreadInstance = new Thread(new CheckAliveThread());
        heartbeatThreadInstance.setName("Client-CheckAliveThread"); // 设置线程名，便于调试
//        heartbeatThreadInstance.setDaemon(true); // 设置为守护线程，主程序退出时自动结束
        heartbeatThreadInstance.start();
        if (isDebugMode) {
            System.out.println("CheckAliveThread started.");
        }
    }

    /**
     * 停止心跳线程并关闭相关连接。
     * 此方法是线程安全的，可以被多次调用。
     */
    public static synchronized void stopThread() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        if (isDebugMode) {
            System.out.println("Stopping CheckAliveThread...");
        }

        // 中断线程，使其能从 sleep 或阻塞状态中立即醒来
        if (heartbeatThreadInstance != null) {
            heartbeatThreadInstance.interrupt();
        }

        // 关闭底层的 Socket 连接
        close(NeoLink.hookSocket);
        if (isDebugMode) {
            System.out.println("CheckAliveThread stopped.");
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                // 1. 发送心跳包
                NeoLink.hookSocket.sendStr(HEARTBEAT_PACKET);
                // System.out.println("Sent PING to server."); // 可选的调试日志

            } catch (Exception e) {
                // 2. 发送失败，通常意味着连接已断开
                if (isDebugMode) {
                    System.err.println("Failed to send heartbeat. Connection to server is lost.");
                }
                debugOperation(e); // 保留您原有的调试逻辑

                // 触发停止流程，关闭连接并退出线程
                stopThread();
                break; // 退出 while 循环
            }

            // 3. 等待下一次发送
            Sleeper.sleep(HEARTBEAT_PACKET_DELAY);
        }
    }
}