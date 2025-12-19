package neoproxy.neolink.threads;

import neoproxy.neolink.NeoLink;
import fun.ceroxe.api.utils.Sleeper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static neoproxy.neolink.Debugger.debugOperation;
import static neoproxy.neolink.InternetOperator.close;
import static neoproxy.neolink.NeoLink.isDebugMode;

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