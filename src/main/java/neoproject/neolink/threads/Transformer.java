package neoproject.neolink.threads;

import neoproject.neolink.NeoLink;
import neoproject.publicInstance.DataPacket;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static neoproject.neolink.NeoLink.*;

public class Transformer implements Runnable {
    public static int BUFFER_LEN = 117;
    private final Socket sender;
    private final Socket receiver;
    public int mode;
    public static final int LOCAL_TO_NEO = 0;
    public static final int NEO_TO_LOCAL = 1;
    public final ObjectOutputStream objectOutputStream;
    public final ObjectInputStream objectInputStream;

    public Transformer(Socket sender, Socket receiver, int mode, ObjectOutputStream objectOutputStream, ObjectInputStream objectInputStream) {
        this.sender = sender;
        this.receiver = receiver;
        this.mode = mode;
        this.objectOutputStream = objectOutputStream;
        this.objectInputStream = objectInputStream;
    }

    @Override
    public void run() {//对象流一定都是AtomServer服务端的，对象流参数前面的Socket一定是通向AtomServer的
        if (this.mode == Transformer.NEO_TO_LOCAL) {
            transferDataToLocalServer(sender, objectInputStream, receiver);
        } else {
            transferDataToAtomServer(sender, receiver, objectOutputStream);
        }
    }

    public static void transferDataToAtomServer(Socket sender, Socket receiver, ObjectOutputStream objectOutputStream) {
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(sender.getInputStream());

            int len;
            byte[] data = new byte[BUFFER_LEN];
            while ((len = bufferedInputStream.read(data)) != -1) {

//                System.out.println("transferDataToAtomServer---------------------------" + Thread.currentThread().getName());
//                System.out.println(new String(data));
//                System.out.println("transferDataToAtomServer---------------------------" + Thread.currentThread().getName());

                DataPacket dataPacket = new DataPacket(len, NeoLink.aesUtil.encrypt(data, 0, len));
                objectOutputStream.writeObject(dataPacket);
                objectOutputStream.flush();
            }

            objectOutputStream.writeObject(null);//tell atom server is end!
            receiver.shutdownOutput();
            sender.shutdownInput();

        } catch (Exception e) {
            NeoLink.debugOperation(e);

            try {

                objectOutputStream.writeObject(null);//tell atom server is end!
                receiver.shutdownOutput();
                sender.shutdownInput();

            } catch (IOException ignore) {
            }

        }
    }

    public static void transferDataToLocalServer(Socket sender, ObjectInputStream objectInputStream, Socket receiver) {
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(receiver.getOutputStream());

            DataPacket dataPacket;
            while ((dataPacket = (DataPacket) objectInputStream.readObject()) != null) {
                byte[] deRealData = NeoLink.aesUtil.decrypt(dataPacket.enData);

                deRealData = subByte(deRealData, dataPacket.realLen);

//                System.out.println("transferDataToLocalServer---------------" + Thread.currentThread().getName());
//                System.out.println(new String(deRealData));
//                System.out.println("transferDataToLocalServer---------------" + Thread.currentThread().getName());

                bufferedOutputStream.write(deRealData);
                bufferedOutputStream.flush();
            }
            sender.shutdownInput();
            receiver.shutdownOutput();
        } catch (Exception e) {//EOF EXCEPTION !
            NeoLink.debugOperation(e);

            try {
                sender.shutdownInput();
                receiver.shutdownOutput();
            } catch (IOException ignore) {
            }
        }
    }

    public static byte[] subByte(byte[] data, int len) {//把数组后面的空的0去掉
        byte[] result = new byte[len];
        System.arraycopy(data, 0, result, 0, len);
        return result;
    }
}
