package neoproject.neolink;


import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static neoproject.neolink.NeoLink.aesUtil;
import static neoproject.neolink.NeoLink.debugOperation;

public class InternetOperator {
    public static void sendStr(ObjectOutputStream objectOutputStream, String str) throws IOException {
        objectOutputStream.writeObject(aesUtil.encrypt(str.getBytes(StandardCharsets.UTF_8)));
        objectOutputStream.flush();
    }

    public static String receiveStr(ObjectInputStream objectInputStream) throws ClassNotFoundException, IOException {
        Object enData = objectInputStream.readObject();
        if (enData == null) {
            return null;
        }
        return new String(aesUtil.decrypt((byte[]) enData), StandardCharsets.UTF_8);
    }

    public static void closeSocket(Closeable... closeables) {
        for (Closeable a : closeables) {
            try {
                a.close();
            } catch (IOException e) {
                debugOperation(e);
            }
        }
    }

    public static String getInternetAddressAndPort(Socket socket) {
        return socket.getInetAddress().toString().replaceAll("/", "") + ":" + socket.getPort();
    }

}
