package neoproxy.neolink.network;

import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProxyOperator 测试类
 *
 * 测试范围：
 * 1. 代理配置解析
 * 2. HTTP 代理解析
 * 3. SOCKS 代理解析
 * 4. IPv6 地址解析
 * 5. 代理认证解析
 */
@DisplayName("ProxyOperator 代理操作器测试")
class ProxyOperatorTest {

    private String originalProxyToLocal;
    private String originalProxyToNeo;
    private String originalRemoteDomainName;
    private String originalLocalDomainName;

    @BeforeEach
    void setUp() throws Exception {
        originalProxyToLocal = ProxyOperator.PROXY_IP_TO_LOCAL_SERVER;
        originalProxyToNeo = ProxyOperator.PROXY_IP_TO_NEO_SERVER;
        originalRemoteDomainName = NeoLink.remoteDomainName;
        originalLocalDomainName = NeoLink.localDomainName;

        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = "";
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = "";
    }

    @AfterEach
    void tearDown() {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = originalProxyToLocal;
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = originalProxyToNeo;
        NeoLink.remoteDomainName = originalRemoteDomainName;
        NeoLink.localDomainName = originalLocalDomainName;
    }

    @Test
    @DisplayName("Type.TO_NEO 常量应为 0")
    void testTypeNeoConstant() {
        assertEquals(0, ProxyOperator.Type.TO_NEO);
    }

    @Test
    @DisplayName("Type.TO_LOCAL 常量应为 1")
    void testTypeLocalConstant() {
        assertEquals(1, ProxyOperator.Type.TO_LOCAL);
    }

    @Test
    @DisplayName("init 方法在无代理配置时不应抛出异常")
    void testInitWithNoProxyConfig() {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = "";
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = "";

        assertDoesNotThrow(() -> ProxyOperator.init());
    }

    @Test
    @DisplayName("init 方法在 null 配置时不应抛出异常")
    void testInitWithNullProxyConfig() {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = null;
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = null;

        assertThrows(NullPointerException.class, () -> ProxyOperator.init());
    }

    @Test
    @DisplayName("init 应正确解析 HTTP 代理配置")
    void testInitParsesHttpProxyConfig() throws Exception {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = "http->192.168.1.1:8080";
        ProxyOperator.init();

        Field typeField = ProxyOperator.class.getDeclaredField("proxyToLocalType");
        typeField.setAccessible(true);
        java.net.Proxy.Type type = (java.net.Proxy.Type) typeField.get(null);

        assertEquals(java.net.Proxy.Type.HTTP, type);
    }

    @Test
    @DisplayName("init 应正确解析 SOCKS 代理配置")
    void testInitParsesSocksProxyConfig() throws Exception {
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = "socks->127.0.0.1:1080";
        ProxyOperator.init();

        Field typeField = ProxyOperator.class.getDeclaredField("proxyToNeoType");
        typeField.setAccessible(true);
        java.net.Proxy.Type type = (java.net.Proxy.Type) typeField.get(null);

        assertEquals(java.net.Proxy.Type.SOCKS, type);
    }

    @Test
    @DisplayName("init 应正确解析带认证的代理配置")
    void testInitParsesProxyWithAuth() throws Exception {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = "http->proxy.example.com:3128@user;pass123";
        ProxyOperator.init();

        Field usernameField = ProxyOperator.class.getDeclaredField("proxyToLocalUsername");
        usernameField.setAccessible(true);
        Field passwordField = ProxyOperator.class.getDeclaredField("proxyToLocalPassword");
        passwordField.setAccessible(true);

        String username = (String) usernameField.get(null);
        String password = (String) passwordField.get(null);

        assertEquals("user", username);
        assertEquals("pass123", password);
    }

    @Test
    @DisplayName("init 应正确解析 IPv6 地址代理配置")
    void testInitParsesIPv6ProxyConfig() throws Exception {
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = "socks->[::1]:1080";
        ProxyOperator.init();

        Field ipField = ProxyOperator.class.getDeclaredField("proxyToNeoIp");
        ipField.setAccessible(true);
        Field portField = ProxyOperator.class.getDeclaredField("proxyToNeoPort");
        portField.setAccessible(true);

        String ip = (String) ipField.get(null);
        int port = (int) portField.get(null);

        assertEquals("::1", ip);
        assertEquals(1080, port);
    }

    @Test
    @DisplayName("init 应正确解析 IPv6 地址带认证的代理配置")
    void testInitParsesIPv6ProxyWithAuth() throws Exception {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = "http->[2001:db8::1]:8080@admin;secret";
        ProxyOperator.init();

        Field ipField = ProxyOperator.class.getDeclaredField("proxyToLocalIp");
        ipField.setAccessible(true);
        Field usernameField = ProxyOperator.class.getDeclaredField("proxyToLocalUsername");
        usernameField.setAccessible(true);

        String ip = (String) ipField.get(null);
        String username = (String) usernameField.get(null);

        assertEquals("2001:db8::1", ip);
        assertEquals("admin", username);
    }

    @Test
    @DisplayName("init 应将未知类型设为 DIRECT")
    void testInitUnknownProxyTypeDefaultsToDirect() throws Exception {
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = "unknown->127.0.0.1:8080";
        ProxyOperator.init();

        Field typeField = ProxyOperator.class.getDeclaredField("proxyToNeoType");
        typeField.setAccessible(true);
        java.net.Proxy.Type type = (java.net.Proxy.Type) typeField.get(null);

        assertEquals(java.net.Proxy.Type.DIRECT, type);
    }

    @Test
    @DisplayName("init 应正确解析标准 IPv4 代理配置")
    void testInitParsesStandardIPv4ProxyConfig() throws Exception {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = "http->10.0.0.1:8888";
        ProxyOperator.init();

        Field ipField = ProxyOperator.class.getDeclaredField("proxyToLocalIp");
        ipField.setAccessible(true);
        Field portField = ProxyOperator.class.getDeclaredField("proxyToLocalPort");
        portField.setAccessible(true);

        String ip = (String) ipField.get(null);
        int port = (int) portField.get(null);

        assertEquals("10.0.0.1", ip);
        assertEquals(8888, port);
    }

    @Test
    @DisplayName("同时配置两个代理时应分别解析")
    void testInitParsesBothProxies() throws Exception {
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = "http->192.168.1.1:8080";
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = "socks->127.0.0.1:1080";
        ProxyOperator.init();

        Field localTypeField = ProxyOperator.class.getDeclaredField("proxyToLocalType");
        localTypeField.setAccessible(true);
        Field neoTypeField = ProxyOperator.class.getDeclaredField("proxyToNeoType");
        neoTypeField.setAccessible(true);

        java.net.Proxy.Type localType = (java.net.Proxy.Type) localTypeField.get(null);
        java.net.Proxy.Type neoType = (java.net.Proxy.Type) neoTypeField.get(null);

        assertEquals(java.net.Proxy.Type.HTTP, localType);
        assertEquals(java.net.Proxy.Type.SOCKS, neoType);
    }
}
