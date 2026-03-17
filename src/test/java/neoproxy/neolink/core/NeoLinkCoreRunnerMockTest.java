package neoproxy.neolink.core;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.network.ProxyOperator;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NeoLinkCoreRunner Mock 测试
 * 
 * 使用 mockito-inline 来 mock 静态方法
 */
@DisplayName("NeoLinkCoreRunner Mock 测试")
class NeoLinkCoreRunnerMockTest {

    @BeforeEach
    void setUp() throws Exception {
        NeoLink.languageData = new LanguageData();
        NeoLink.isDebugMode = false;
        NeoLink.isGUIMode = true;
        NeoLink.enableAutoReconnect = false;
        NeoLink.hostHookPort = 44801;
        NeoLink.remoteDomainName = "localhost";
        
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        shouldStopField.set(null, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        NeoLink.languageData = null;
        
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        shouldStopField.set(null, false);
    }

    @Test
    @DisplayName("runCore 应在 shouldStop 为 true 时立即退出")
    void testRunCoreShouldStopImmediately() throws Exception {
        NeoLinkCoreRunner.requestStop();
        
        Method method = NeoLinkCoreRunner.class.getDeclaredMethod("runCore", String.class, int.class, String.class);
        method.setAccessible(true);
        
        assertDoesNotThrow(() -> method.invoke(null, "localhost", 8080, "test-key"));
    }

    @Test
    @DisplayName("runCore 应在连接失败时正确处理异常")
    void testRunCoreConnectionFailure() throws Exception {
        try (MockedStatic<ProxyOperator> proxyMock = mockStatic(ProxyOperator.class);
             MockedStatic<SecureSocket> socketMock = mockStatic(SecureSocket.class)) {
            
            proxyMock.when(ProxyOperator::init).thenAnswer(inv -> null);
            proxyMock.when(() -> ProxyOperator.PROXY_IP_TO_NEO_SERVER).thenReturn("");
            
            socketMock.when(() -> new SecureSocket(anyString(), anyInt()))
                .thenThrow(new IOException("Connection refused"));
            
            NeoLink.enableAutoReconnect = false;
            
            Method method = NeoLinkCoreRunner.class.getDeclaredMethod("runCore", String.class, int.class, String.class);
            method.setAccessible(true);
            
            assertDoesNotThrow(() -> method.invoke(null, "localhost", 8080, "test-key"));
        }
    }

    @Test
    @DisplayName("runCore 应正确设置 NeoLink 静态字段")
    void testRunCoreSetsStaticFields() throws Exception {
        NeoLinkCoreRunner.requestStop();
        
        Method method = NeoLinkCoreRunner.class.getDeclaredMethod("runCore", String.class, int.class, String.class);
        method.setAccessible(true);
        
        method.invoke(null, "example.com", 9090, "my-key");
        
        assertEquals("example.com", NeoLink.remoteDomainName);
        assertEquals(9090, NeoLink.localPort);
        assertEquals("my-key", NeoLink.key);
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
    @DisplayName("runCore 应在重连等待期间响应 stop 请求")
    void testRunCoreStopsDuringReconnectWait() throws Exception {
        try (MockedStatic<ProxyOperator> proxyMock = mockStatic(ProxyOperator.class);
             MockedStatic<SecureSocket> socketMock = mockStatic(SecureSocket.class)) {
            
            proxyMock.when(ProxyOperator::init).thenAnswer(inv -> null);
            proxyMock.when(() -> ProxyOperator.PROXY_IP_TO_NEO_SERVER).thenReturn("");
            
            socketMock.when(() -> new SecureSocket(anyString(), anyInt()))
                .thenThrow(new IOException("Connection refused"));
            
            NeoLink.enableAutoReconnect = true;
            NeoLink.reconnectionIntervalSeconds = 1;
            
            Thread testThread = Thread.ofVirtual().start(() -> {
                try {
                    Method method = NeoLinkCoreRunner.class.getDeclaredMethod("runCore", String.class, int.class, String.class);
                    method.setAccessible(true);
                    method.invoke(null, "localhost", 8080, "test-key");
                } catch (Exception ignored) {
                }
            });
            
            Thread.sleep(100);
            NeoLinkCoreRunner.requestStop();
            
            testThread.join(2000);
            assertFalse(testThread.isAlive());
        }
    }
}
