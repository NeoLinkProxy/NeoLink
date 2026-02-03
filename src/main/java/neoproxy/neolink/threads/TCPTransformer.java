package neoproxy.neolink.threads;

import fun.ceroxe.api.net.SecureSocket;

import java.net.Socket;

import static neoproxy.neolink.Debugger.debugOperation;
import static neoproxy.neolink.InternetOperator.*;

/**
 * 数据传输器，负责在本地服务和 Neo 服务器之间双向转发数据。
 * 【优化版】通过复用实例缓冲区来减少GC压力。
 * 【新功能】支持 Proxy Protocol v2 的剥离或透传。
 */
public class TCPTransformer implements Runnable {
    public static final int MODE_NEO_TO_LOCAL = 0;
    public static final int MODE_LOCAL_TO_NEO = 1;
    // Proxy Protocol v2 的 12 字节固定签名
    private static final byte[] PPV2_SIG = new byte[]{
            (byte) 0x0D, (byte) 0x0A, (byte) 0x0D, (byte) 0x0A,
            (byte) 0x00, (byte) 0x0D, (byte) 0x0A, (byte) 0x51,
            (byte) 0x55, (byte) 0x49, (byte) 0x54, (byte) 0x0A
    };
    public static int BUFFER_LENGTH = 65535; // 可以保持为静态常量
    private final Socket plainSocket;
    private final SecureSocket secureSocket;
    private final int mode;
    private final boolean enableProxyProtocol;

    // 🔥【性能优化】为每个实例创建一个独立的、可复用的缓冲区
    private final byte[] buffer = new byte[BUFFER_LENGTH];

    /**
     * 构造函数：用于从 Neo 服务器接收数据并转发到本地服务。
     *
     * @param enableProxyProtocol 是否允许透传 Proxy Protocol 头
     */
    public TCPTransformer(SecureSocket secureSender, Socket localReceiver, boolean enableProxyProtocol) {
        this.secureSocket = secureSender;
        this.plainSocket = localReceiver;
        this.mode = MODE_NEO_TO_LOCAL;
        this.enableProxyProtocol = enableProxyProtocol;
    }

    /**
     * 构造函数：用于从本地服务接收数据并转发到 Neo 服务器。
     *
     * @param enableProxyProtocol 此方向通常不使用，可传 false
     */
    public TCPTransformer(Socket localSender, SecureSocket secureReceiver, boolean enableProxyProtocol) {
        this.plainSocket = localSender;
        this.secureSocket = secureReceiver;
        this.mode = MODE_LOCAL_TO_NEO;
        this.enableProxyProtocol = enableProxyProtocol;
    }

    /**
     * 将本地数据转发到 Neo 服务器 (Local -> Neo)
     */
    private void transferDataToNeoServer() {
        // 修改：直接获取 InputStream，不要包裹 BufferedInputStream
        try (var inputFromLocal = plainSocket.getInputStream()) {
            int bytesRead;
            // 🔥 使用实例的 buffer，实现对象复用
            // 直接从 Socket 读入 64KB buffer，减少内存拷贝和系统调用
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
     * 将 Neo 服务器数据转发到本地 (Neo -> Local)
     * 【核心逻辑】在此处检测并处理 Proxy Protocol 头
     */
    private void transferDataToLocalServer() {
        // 修改：直接获取 OutputStream，不要包裹 BufferedOutputStream
        try (var outputToLocal = plainSocket.getOutputStream()) {
            byte[] data;
            boolean isFirstPacket = true;

            while ((data = secureSocket.receiveByte()) != null) {
                if (data.length == 0) continue;

                if (isFirstPacket) {
                    isFirstPacket = false;
                    // 检测是否是 Proxy Protocol v2 头
                    if (isProxyProtocolV2Signature(data)) {
                        if (this.enableProxyProtocol) {
                            // 配置为开启：透传给本地后端
                            outputToLocal.write(data);
                        } else {
                            // 配置为关闭：丢弃该数据包
                            continue;
                        }
                    } else {
                        // 不是 PP 头，正常写入
                        outputToLocal.write(data);
                    }
                } else {
                    // 后续数据正常写入
                    outputToLocal.write(data);
                }

                // 移除 flush()，因为 SocketOutputStream 默认是直接发送的，且没有 Buffer 就不需要 flush
                // outputToLocal.flush();
            }
            shutdownInput(secureSocket);
            shutdownOutput(plainSocket);
        } catch (Exception e) {
            debugOperation(e);
            shutdownInput(secureSocket);
            shutdownOutput(plainSocket);
        }
    }

    /**
     * 检查数据包是否以 Proxy Protocol v2 签名开头
     */
    private boolean isProxyProtocolV2Signature(byte[] data) {
        if (data == null || data.length < 12) {
            return false;
        }
        for (int i = 0; i < 12; i++) {
            if (data[i] != PPV2_SIG[i]) {
                return false;
            }
        }
        return true;
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
            // 无论正常结束还是异常结束，都确保关闭资源
            close(plainSocket, secureSocket);
        }
    }
}