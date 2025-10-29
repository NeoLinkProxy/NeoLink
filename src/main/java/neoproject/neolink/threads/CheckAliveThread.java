package neoproject.neolink.threads;

import neoproject.neolink.NeoLink;
import plethora.utils.Sleeper;

import static neoproject.neolink.InternetOperator.close;
import static neoproject.neolink.NeoLink.debugOperation;

public class CheckAliveThread implements Runnable {
    public static int HEARTBEAT_PACKET_DELAY = 1000;
    public static boolean isRunning = false;

    public CheckAliveThread() {
    }

    public static void startThread() {
        isRunning = true;
        Thread a = new Thread(new CheckAliveThread());
        a.start();
    }

    public static void stopThread() {
        isRunning = false;
        close(NeoLink.hookSocket);
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                NeoLink.hookSocket.sendRaw(new byte[]{0});
            } catch (Exception e) {
                debugOperation(e);
                break;
            }
            Sleeper.sleep(HEARTBEAT_PACKET_DELAY);
        }
    }
}
