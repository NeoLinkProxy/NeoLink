package neoproxy.neolink.network;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.core.NeoLink;

import java.io.IOException;
import java.net.*;

import static neoproxy.neolink.core.NeoLink.localDomainName;
import static neoproxy.neolink.core.NeoLink.remoteDomainName;

/**
 * 代理操作器
 *
 * 核心职责：
 * 1. 处理通过 HTTP 或 SOCKS 代理连接到 Neo 服务器
 * 2. 处理通过代理连接到本地服务
 * 3. 支持代理认证（用户名/密码）
 *
 * 设计特点：
 * - 支持 HTTP 和 SOCKS 两种代理类型
 * - 独立的代理配置：到本地服务和到 Neo 服务器可分别配置
 * - 支持代理认证
 * - 自动处理代理连接的创建和配置
 *
 * 使用场景：
 * - 企业网络需要通过代理访问外网
 * - 本地服务位于代理后的网络
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
public class ProxyOperator {

    // 代理到本地服务的配置
    public static String PROXY_IP_TO_LOCAL_SERVER = "";
    // 代理到 Neo 服务器的配置
    public static String PROXY_IP_TO_NEO_SERVER = "";
    private static Proxy.Type proxyToLocalType = null;
    private static String proxyToLocalIp = null;
    private static int proxyToLocalPort;
    private static String proxyToLocalUsername = null;
    private static String proxyToLocalPassword = null;
    private static Proxy.Type proxyToNeoType = null;
    private static String proxyToNeoIp = null;
    private static int proxyToNeoPort;
    private static String proxyToNeoUsername = null;
    private static String proxyToNeoPassword = null;

    /**
     * 初始化代理配置，解析命令行或配置文件中提供的代理字符串。
     */
    public static void init() {
        resetParsedProxyState();
        if (hasText(PROXY_IP_TO_LOCAL_SERVER)) {
            parseProxyConfig(PROXY_IP_TO_LOCAL_SERVER, true);
        }
        if (hasText(PROXY_IP_TO_NEO_SERVER)) {
            parseProxyConfig(PROXY_IP_TO_NEO_SERVER, false);
        }
    }

    private static void resetParsedProxyState() {
        proxyToLocalType = null;
        proxyToLocalIp = null;
        proxyToLocalPort = 0;
        proxyToLocalUsername = null;
        proxyToLocalPassword = null;
        proxyToNeoType = null;
        proxyToNeoIp = null;
        proxyToNeoPort = 0;
        proxyToNeoUsername = null;
        proxyToNeoPassword = null;
    }

    private static void parseProxyConfig(String proxyConfig, boolean isLocalProxy) {
        if (!hasText(proxyConfig)) {
            return;
        }

        String[] typeAndProperty = proxyConfig.split("->", 2);
        if (typeAndProperty.length != 2 || !hasText(typeAndProperty[1])) {
            throw new IllegalArgumentException("Invalid proxy format. Expected type->host:port[@user;password].");
        }

        Proxy.Type proxyType;
        if ("socks".equals(typeAndProperty[0])) {
            proxyType = Proxy.Type.SOCKS;
        } else if ("http".equals(typeAndProperty[0])) {
            proxyType = Proxy.Type.HTTP;
        } else {
            proxyType = Proxy.Type.DIRECT;
        }

        String[] authParts = typeAndProperty[1].split("@", 2);
        String ip;
        int port;

        if (authParts[0].startsWith("[")) {
            int closingBracket = authParts[0].indexOf(']');
            if (closingBracket <= 1 || closingBracket + 2 > authParts[0].length() || authParts[0].charAt(closingBracket + 1) != ':') {
                throw new IllegalArgumentException("Invalid IPv6 proxy address: " + authParts[0]);
            }
            ip = authParts[0].substring(1, closingBracket);
            port = parseProxyPort(authParts[0].substring(closingBracket + 2));
        } else {
            String[] ipPortParts = authParts[0].split(":", 2);
            if (ipPortParts.length != 2 || !hasText(ipPortParts[0])) {
                throw new IllegalArgumentException("Invalid proxy address: " + authParts[0]);
            }
            ip = ipPortParts[0];
            port = parseProxyPort(ipPortParts[1]);
        }

        String username = null;
        String password = null;
        if (authParts.length > 1) {
            String[] userPass = authParts[1].split(";", 2);
            if (userPass.length != 2) {
                throw new IllegalArgumentException("Invalid proxy authentication format. Expected user;password.");
            }
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int parseProxyPort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Proxy port must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Proxy port must be an integer.", e);
        }
    }

    /**
     * 创建一个经过代理处理的普通 Socket。
     */
    public synchronized static Socket getHandledSocket(int socketType, int targetPort) throws IOException {
        ProxySettings settings = settingsFor(socketType);
        return withProxyAuthenticator(settings, () -> {
            Socket socket = new Socket(settings.proxy());
            socket.connect(new InetSocketAddress(settings.targetHost(), targetPort));
            return socket;
        });
    }

    /**
     * 创建一个经过代理处理的 SecureSocket。
     */
    public synchronized static SecureSocket getHandledSecureSocket(int socketType, int targetPort) throws IOException {
        ProxySettings settings = settingsFor(socketType);
        return withProxyAuthenticator(settings, () -> new SecureSocket(settings.proxy(), settings.targetHost(), targetPort));
    }

    private static ProxySettings settingsFor(int socketType) {
        if (socketType == Type.TO_NEO) {
            return new ProxySettings(proxyToNeoType, proxyToNeoIp, proxyToNeoPort, remoteDomainName, proxyToNeoUsername, proxyToNeoPassword);
        }
        return new ProxySettings(proxyToLocalType, proxyToLocalIp, proxyToLocalPort, localDomainName, proxyToLocalUsername, proxyToLocalPassword);
    }

    private static <T> T withProxyAuthenticator(ProxySettings settings, IOExceptionSupplier<T> supplier) throws IOException {
        if (!settings.hasCredentials()) {
            return supplier.get();
        }

        Authenticator previous = Authenticator.getDefault();
        try {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.username(), settings.password().toCharArray());
                }
            });
            return supplier.get();
        } finally {
            Authenticator.setDefault(previous);
        }
    }

    private record ProxySettings(
            Proxy.Type proxyType,
            String proxyHost,
            int proxyPort,
            String targetHost,
            String username,
            String password
    ) {
        Proxy proxy() {
            if (proxyType == null || proxyType == Proxy.Type.DIRECT) {
                return Proxy.NO_PROXY;
            }
            return new Proxy(proxyType, new InetSocketAddress(proxyHost, proxyPort));
        }

        boolean hasCredentials() {
            return proxyType != null && proxyType != Proxy.Type.DIRECT && username != null && password != null;
        }
    }

    @FunctionalInterface
    private interface IOExceptionSupplier<T> {
        T get() throws IOException;
    }

    public static class Type {
        public static final int TO_NEO = 0;
        public static final int TO_LOCAL = 1;
    }
}
