package neoproject.neolink.gui;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 线程安全的日志消息队列。
 * NeoLink 的核心逻辑将日志发送到此队列，GUI 从队列中消费并显示。
 */
public class LogMessageQueue {
    private static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public static void offer(String message) {
        queue.offer(message);
    }

    public static String take() throws InterruptedException {
        return queue.take();
    }

    public static boolean isEmpty() {
        return queue.isEmpty();
    }
}