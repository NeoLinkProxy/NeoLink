package neoproject.neolink.threads;

import neoproject.neolink.NeoLink;
import plethora.net.SecureSocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Transformer implements Runnable {
    public static int BUFFER_LEN = 117;
    //
    private Socket sender;
    private Socket receiver;

    private SecureSocket secureSender;
    private SecureSocket secureReceiver;

    public int mode;
    public static final int LOCAL_TO_NEO = 0;
    public static final int NEO_TO_LOCAL = 1;

    public Transformer(SecureSocket sender, Socket receiver) {
        this.secureSender = sender;
        this.receiver = receiver;
        this.mode = NEO_TO_LOCAL;
    }

    public Transformer(Socket sender, SecureSocket receiver) {
        this.sender = sender;
        this.secureReceiver = receiver;
        this.mode = LOCAL_TO_NEO;
    }

    @Override
    public void run() {//对象流一定都是AtomServer服务端的，对象流参数前面的Socket一定是通向AtomServer的
        if (this.mode == Transformer.NEO_TO_LOCAL) {
            transferDataToLocalServer(secureSender, receiver);
        } else {
            transferDataToNeoServer(sender, secureReceiver);
        }
    }

    public static void transferDataToNeoServer(Socket sender, SecureSocket secureReceiver) {
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(sender.getInputStream());

            int realLen;
            byte[] data = new byte[BUFFER_LEN];
            while ((realLen = bufferedInputStream.read(data)) != -1) {
                secureReceiver.sendByte(data, 0, realLen);
                ;
            }

            secureReceiver.sendByte(null);//告知传输完毕
            sender.shutdownInput();

        } catch (Exception e) {
            NeoLink.debugOperation(e);

            try {

                secureReceiver.shutdownOutput();//传输出现错误
                sender.shutdownInput();

            } catch (IOException ignore) {
            }

        }
    }

    public static void transferDataToLocalServer(SecureSocket sender, Socket receiver) {
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(receiver.getOutputStream());

            byte[] data;
            while ((data = sender.receiveByte()) != null) {
                bufferedOutputStream.write(data);
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

    public SecureSocket getSecureReceiver() {
        return secureReceiver;
    }

    public SecureSocket getSecureSender() {
        return secureSender;
    }
}
