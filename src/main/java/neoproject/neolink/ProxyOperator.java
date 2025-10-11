package neoproject.neolink;

import plethora.net.SecureSocket;

import java.io.IOException;
import java.net.*;

import static neoproject.neolink.NeoLink.localDomainName;
import static neoproject.neolink.NeoLink.remoteDomainName;

/**
 * 代理操作器，用于处理通过 HTTP 或 SOCKS 代理连接到 Neo 服务器或本地服务。
 */
public class ProxyOperator {

    // 代理到本地服务的配置
    public static String PROXY_IP_TO_LOCAL_SERVER = null;
    private static Proxy.Type proxyToLocalType = null;
    private static String proxyToLocalIp = null;
    private static int proxyToLocalPort;
    private static String proxyToLocalUsername = null;
    private static String proxyToLocalPassword = null;

    // 代理到 Neo 服务器的配置
    public static String PROXY_IP_TO_NEO_SERVER = null;
    private static Proxy.Type proxyToNeoType = null;
    private static String proxyToNeoIp = null;
    private static int proxyToNeoPort;
    private static String proxyToNeoUsername = null;
    private static String proxyToNeoPassword = null;

    /**
     * 初始化代理配置，解析命令行或配置文件中提供的代理字符串。
     */
    public static void init() {
        if (PROXY_IP_TO_LOCAL_SERVER != null) {
            parseProxyConfig(PROXY_IP_TO_LOCAL_SERVER, true);
        }
        if (PROXY_IP_TO_NEO_SERVER != null) {
            parseProxyConfig(PROXY_IP_TO_NEO_SERVER, false);
        }
    }

    private static void parseProxyConfig(String proxyConfig, boolean isLocalProxy) {
        String[] typeAndProperty = proxyConfig.split("->", 2);
        Proxy.Type proxyType;
        if ("socks".equals(typeAndProperty[0])) {
            proxyType = Proxy.Type.SOCKS;
        } else if ("http".equals(typeAndProperty[0])) {
            proxyType = Proxy.Type.HTTP;
        } else {
            proxyType = Proxy.Type.DIRECT;
        }

        String[] authParts = typeAndProperty[1].split("@", 2);
        String[] ipPortParts = authParts[0].split(":", 2);
        String ip = ipPortParts[0];
        int port = Integer.parseInt(ipPortParts[1]);

        String username = null;
        String password = null;
        if (authParts.length > 1) {
            String[] userPass = authParts[1].split(";", 2);
            username = userPass[0];
            password = userPass[1];
        }

        if (isLocalProxy) {
            proxyToLocalType = proxyType;
            proxyToLocalIp = ip;
            proxyToLocalPort = port;
            proxyToLocalUsername = username;
            proxyToLocalPassword = password;
        } else {
            proxyToNeoType = proxyType;
            proxyToNeoIp = ip;
            proxyToNeoPort = port;
            proxyToNeoUsername = username;
            proxyToNeoPassword = password;
        }
    }

    /**
     * 创建一个经过代理处理的普通 Socket。
     */
    public synchronized static Socket getHandledSocket(int socketType, int targetPort) throws IOException {
        Proxy proxy;
        String targetHost;
        int proxyPort;
        String proxyUsername;
        String proxyPassword;

        if (socketType == Type.TO_NEO) {
            proxy = new Proxy(proxyToNeoType, new InetSocketAddress(proxyToNeoIp, proxyToNeoPort));
            targetHost = remoteDomainName;
            proxyPort = proxyToNeoPort;
            proxyUsername = proxyToNeoUsername;
            proxyPassword = proxyToNeoPassword;
        } else {
            proxy = new Proxy(proxyToLocalType, new InetSocketAddress(proxyToLocalIp, proxyToLocalPort));
            targetHost = localDomainName;
            proxyPort = proxyToLocalPort;
            proxyUsername = proxyToLocalUsername;
            proxyPassword = proxyToLocalPassword;
        }

        if (proxyUsername != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyUsername, proxyPassword.toCharArray());
                }
            });
        }

        Socket socket = new Socket(proxy);
        socket.connect(new InetSocketAddress(targetHost, targetPort));
        return socket;
    }

    /**
     * 创建一个经过代理处理的 SecureSocket。
     */
    public synchronized static SecureSocket getHandledSecureSocket(int socketType, int targetPort) throws IOException {
        Proxy proxy;
        String targetHost;
        int proxyPort;
        String proxyUsername;
        String proxyPassword;

        if (socketType == Type.TO_NEO) {
            proxy = new Proxy(proxyToNeoType, new InetSocketAddress(proxyToNeoIp, proxyToNeoPort));
            targetHost = remoteDomainName;
            proxyPort = proxyToNeoPort;
            proxyUsername = proxyToNeoUsername;
            proxyPassword = proxyToNeoPassword;
        } else {
            proxy = new Proxy(proxyToLocalType, new InetSocketAddress(proxyToLocalIp, proxyToLocalPort));
            targetHost = localDomainName; // 修复：这里应该是 localDomainName
            proxyPort = proxyToLocalPort;
            proxyUsername = proxyToLocalUsername;
            proxyPassword = proxyToLocalPassword;
        }

        if (proxyUsername != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyUsername, proxyPassword.toCharArray());
                }
            });
        }

        return new SecureSocket(proxy, targetHost, targetPort);
    }

    public static class Type {
        public static final int TO_NEO = 0;
        public static final int TO_LOCAL = 1;
    }
}