package neoproject.neolink;


import plethora.net.SecureSocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

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

    public static void shutdownInput(SecureSocket socket) {
        try {
            socket.shutdownInput();
        } catch (Exception ignore) {
        }
    }

    public static void shutdownInput(Socket socket) {
        try {
            socket.shutdownInput();
        } catch (Exception ignore) {
        }
    }

    public static void shutdownOutput(SecureSocket socket) {
        try {
            socket.shutdownOutput();
        } catch (Exception ignore) {
        }
    }

    public static void shutdownOutput(Socket socket) {
        try {
            socket.shutdownOutput();
        } catch (Exception ignore) {
        }
    }


    public static byte[] receiveBytes() throws IOException {
        return hookSocket.receiveByte();
    }
}
