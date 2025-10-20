package neoproject.neolink.threads;

import neoproject.neolink.InternetOperator;
import neoproject.neolink.NeoLink;
import plethora.net.SecureSocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;

/**
 * 数据传输器，负责在本地服务和 Neo 服务器之间双向转发数据。
 */
public class Transformer implements Runnable {
    public static int BUFFER_LENGTH = 4096;
    public static final int MODE_NEO_TO_LOCAL = 0;
    public static final int MODE_LOCAL_TO_NEO = 1;

    private final Socket plainSocket;
    private final SecureSocket secureSocket;
    private final int mode;

    /**
     * 构造函数：用于从 Neo 服务器接收数据并转发到本地服务。
     */
    public Transformer(SecureSocket secureSender, Socket localReceiver) {
        this.secureSocket = secureSender;
        this.plainSocket = localReceiver;
        this.mode = MODE_NEO_TO_LOCAL;
    }

    /**
     * 构造函数：用于从本地服务接收数据并转发到 Neo 服务器。
     */
    public Transformer(Socket localSender, SecureSocket secureReceiver) {
        this.plainSocket = localSender;
        this.secureSocket = secureReceiver;
        this.mode = MODE_LOCAL_TO_NEO;
    }

    @Override
    public void run() {
        if (mode == MODE_NEO_TO_LOCAL) {
            transferDataToLocalServer(secureSocket, plainSocket);
        } else {
            transferDataToNeoServer(plainSocket, secureSocket);
        }
    }

    /**
     * 将数据从本地服务转发到 Neo 服务器。
     */
    public static void transferDataToNeoServer(Socket localSender, SecureSocket neoReceiver) {
        try (BufferedInputStream inputFromLocal = new BufferedInputStream(localSender.getInputStream())) {
            byte[] buffer = new byte[BUFFER_LENGTH];
            int bytesRead;
            while ((bytesRead = inputFromLocal.read(buffer)) != -1) {
                neoReceiver.sendByte(buffer, 0, bytesRead);
            }
            neoReceiver.sendByte(null); // 发送结束信号
            InternetOperator.shutdownInput(localSender);
        } catch (Exception e) {
            NeoLink.debugOperation(e);
            InternetOperator.shutdownOutput(neoReceiver);
            InternetOperator.shutdownInput(localSender);
        }
    }

    /**
     * 将数据从 Neo 服务器转发到本地服务。
     */
    public static void transferDataToLocalServer(SecureSocket neoSender, Socket localReceiver) {
        try (BufferedOutputStream outputToLocal = new BufferedOutputStream(localReceiver.getOutputStream())) {
            byte[] data;
            while ((data = neoSender.receiveByte()) != null) {
                outputToLocal.write(data);
                outputToLocal.flush();
            }
            InternetOperator.shutdownInput(neoSender);
            InternetOperator.shutdownOutput(localReceiver);
        } catch (Exception e) {
            NeoLink.debugOperation(e);
            InternetOperator.shutdownInput(neoSender);
            InternetOperator.shutdownOutput(localReceiver);
        }
    }
}