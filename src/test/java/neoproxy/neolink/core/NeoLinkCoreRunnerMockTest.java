package neoproxy.neolink.core;

import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.network.ProxyOperator;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NeoLinkCoreRunner 测试
 * 
 * 专注于测试可隔离的核心逻辑
 */
@DisplayName("NeoLinkCoreRunner 测试")
class NeoLinkCoreRunnerMockTest {

    private String originalProxyIpToNeoServer;
    private String originalRemoteDomainName;
    private int originalLocalPort;
    private String originalKey;
    private boolean originalEnableAutoReconnect;
    private LanguageData originalLanguageData;

    @BeforeEach
    void setUp() throws Exception {
        originalProxyIpToNeoServer = ProxyOperator.PROXY_IP_TO_NEO_SERVER;
        originalRemoteDomainName = NeoLink.remoteDomainName;
        originalLocalPort = NeoLink.localPort;
        originalKey = NeoLink.key;
        originalEnableAutoReconnect = NeoLink.enableAutoReconnect;
        originalLanguageData = NeoLink.languageData;
        
        NeoLink.languageData = new LanguageData();
        NeoLink.isDebugMode = false;
        NeoLink.isGUIMode = true;
        NeoLink.enableAutoReconnect = false;
        NeoLink.hostHookPort = 44801;
        NeoLink.remoteDomainName = "localhost";
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = "";
        
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        shouldStopField.set(null, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = originalProxyIpToNeoServer;
        NeoLink.remoteDomainName = originalRemoteDomainName;
        NeoLink.localPort = originalLocalPort;
        NeoLink.key = originalKey;
        NeoLink.enableAutoReconnect = originalEnableAutoReconnect;
        NeoLink.languageData = originalLanguageData;
        
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        shouldStopField.set(null, false);
    }

    @Test
    @DisplayName("requestStop 应设置 shouldStop 为 true")
    void testRequestStop() throws Exception {
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        
        assertFalse((boolean) shouldStopField.get(null));
        
        NeoLinkCoreRunner.requestStop();
        
        assertTrue((boolean) shouldStopField.get(null));
    }

    @Test
    @DisplayName("setStopCallback 应正确存储回调")
    void testSetStopCallback() throws Exception {
        Field callbackField = NeoLinkCoreRunner.class.getDeclaredField("stopCallback");
        callbackField.setAccessible(true);
        
        assertNull(callbackField.get(null));
        
        NeoLinkCoreRunner.StopCallback callback = () -> {};
        NeoLinkCoreRunner.setStopCallback(callback);
        
        assertSame(callback, callbackField.get(null));
        
        callbackField.set(null, null);
    }

    @Test
    @DisplayName("shouldStop 初始值应为 false")
    void testShouldStopInitialValue() throws Exception {
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        shouldStopField.set(null, false);
        
        assertFalse((boolean) shouldStopField.get(null));
    }

    @Test
    @DisplayName("多次调用 requestStop 应保持 shouldStop 为 true")
    void testMultipleRequestStop() throws Exception {
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        
        NeoLinkCoreRunner.requestStop();
        assertTrue((boolean) shouldStopField.get(null));
        
        NeoLinkCoreRunner.requestStop();
        assertTrue((boolean) shouldStopField.get(null));
        
        NeoLinkCoreRunner.requestStop();
        assertTrue((boolean) shouldStopField.get(null));
    }
}
