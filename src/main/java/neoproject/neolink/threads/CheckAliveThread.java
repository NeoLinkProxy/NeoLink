package neoproject.neolink.threads;

import neoproject.neolink.NeoLink;
import plethora.utils.Sleeper;

public class CheckAliveThread implements Runnable {
    public static int HEARTBEAT_PACKET_DELAY = 1000;

    public CheckAliveThread() {
    }

    public static void startThread() {
        Thread a = new Thread(new CheckAliveThread());
        a.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                NeoLink.hookSocketWriter.writeObject("");
            } catch (Exception e) {
                if (NeoLink.IS_DEBUG_MODE) {
                    NeoLink.debugOperation(e);
                    break;
                }
            }
            Sleeper.sleep(HEARTBEAT_PACKET_DELAY);
        }
    }
}
