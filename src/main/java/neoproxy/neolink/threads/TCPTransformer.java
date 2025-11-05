package neoproxy.neolink.threads;

import plethora.net.SecureSocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;

import static neoproxy.neolink.InternetOperator.*;
import static neoproxy.neolink.NeoLink.debugOperation;

/**
 * 数据传输器，负责在本地服务和 Neo 服务器之间双向转发数据。
 * 【优化版】通过复用实例缓冲区来减少GC压力。
 */
public class TCPTransformer implements Runnable {
    public static final int MODE_NEO_TO_LOCAL = 0;
    public static final int MODE_LOCAL_TO_NEO = 1;
    public static int BUFFER_LENGTH = 4096; // 可以保持为静态常量

    private final Socket plainSocket;
    private final SecureSocket secureSocket;
    private final int mode;

    // 🔥【性能优化】为每个实例创建一个独立的、可复用的缓冲区
    // 避免在每次数据传输时都创建新的 byte[]，从而减少GC压力
    private final byte[] buffer = new byte[BUFFER_LENGTH];

    /**
     * 构造函数：用于从 Neo 服务器接收数据并转发到本地服务。
     */
    public TCPTransformer(SecureSocket secureSender, Socket localReceiver) {
        this.secureSocket = secureSender;
        this.plainSocket = localReceiver;
        this.mode = MODE_NEO_TO_LOCAL;
    }

    /**
     * 构造函数：用于从本地服务接收数据并转发到 Neo 服务器。
     */
    public TCPTransformer(Socket localSender, SecureSocket secureReceiver) {
        this.plainSocket = localSender;
        this.secureSocket = secureReceiver;
        this.mode = MODE_LOCAL_TO_NEO;
    }

    /**
     * 🔥【重构】将静态方法改为实例方法，用于从本地服务转发数据到 Neo 服务器。
     * 现在使用实例的 buffer，而不是每次创建新的。
     */
    private void transferDataToNeoServer() {
        try (BufferedInputStream inputFromLocal = new BufferedInputStream(plainSocket.getInputStream())) {
            int bytesRead;
            // 🔥 使用实例的 buffer，实现对象复用
            while ((bytesRead = inputFromLocal.read(buffer)) != -1) {
                secureSocket.sendByte(buffer, 0, bytesRead);
            }
            secureSocket.sendByte(null); // 发送结束信号
            shutdownInput(plainSocket);
        } catch (Exception e) {
            debugOperation(e);
            shutdownOutput(secureSocket);
            shutdownInput(plainSocket);
        }
    }

    /**
     * 🔥【重构】将静态方法改为实例方法，用于从 Neo 服务器转发数据到本地服务。
     */
    private void transferDataToLocalServer() {
        try (BufferedOutputStream outputToLocal = new BufferedOutputStream(plainSocket.getOutputStream())) {
            byte[] data;
            while ((data = secureSocket.receiveByte()) != null) {
                outputToLocal.write(data);
                outputToLocal.flush();
            }
            shutdownInput(secureSocket);
            shutdownOutput(plainSocket);
        } catch (Exception e) {
            debugOperation(e);
            shutdownInput(secureSocket);
            shutdownOutput(plainSocket);
        }
    }

    @Override
    public void run() {
        try {
            if (mode == MODE_NEO_TO_LOCAL) {
                transferDataToLocalServer(); // 🔥 调用实例方法
            } else {
                transferDataToNeoServer();  // 🔥 调用实例方法
            }
        } catch (Exception e) {
            debugOperation(e);
        } finally {
            // 最终修复：无论正常结束还是异常结束，都确保关闭资源
            // 这会通知另一个方向的流，使其也快速退出
            close(plainSocket, secureSocket);
        }
    }
}