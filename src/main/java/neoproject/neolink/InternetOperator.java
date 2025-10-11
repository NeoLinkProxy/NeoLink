package neoproject.neolink;


import java.io.Closeable;
import java.io.IOException;

import static neoproject.neolink.NeoLink.debugOperation;
import static neoproject.neolink.NeoLink.hookSocket;

public class InternetOperator {
    public static void sendStr(String str) throws IOException {
        hookSocket.sendStr(str);
    }

    public static String receiveStr() throws IOException {
        return hookSocket.receiveStr();
    }

    public static void closeSocket(Closeable... closeables) {
        for (Closeable a : closeables) {
            try {
                if (a != null) {
                    a.close();
                }
            } catch (IOException e) {
                debugOperation(e);
            }
        }
    }

    public static byte[] receiveBytes() throws IOException {
        return hookSocket.receiveByte();
    }
}
