package neoproxy.neolink.network;


import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.core.NeoLink;
import neoproxy.neolink.util.Debugger;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import static neoproxy.neolink.util.Debugger.debugOperation;
import static neoproxy.neolink.core.NeoLink.hookSocket;

/**
 * 网络操作器
 *
 * 核心职责：
 * 1. 封装与 Neo 服务器的通信操作
 * 2. 提供安全的资源关闭方法
 * 3. 处理网络异常和调试信息
 *
 * 设计特点：
 * - 简化 SecureSocket 的使用
 * - 统一的异常处理
 * - 安全的资源释放
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
public class InternetOperator {
    public static void sendStr(String str) throws IOException {
        hookSocket.sendStr(str);
    }

    public static String receiveStr() throws IOException {
        return hookSocket.receiveStr();
    }

    public static void close(Closeable... closeables) {
        for (Closeable a : closeables) {
            try {
                if (a != null) {
                    a.close();
                }
            } catch (Exception e) {
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
        return hookSocket.receiveBytes();
    }
}
