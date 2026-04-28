package neoproxy.neolink.network;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * InternetOperator 测试类
 *
 * 测试范围：
 * 1. 字符串发送/接收
 * 2. 资源关闭
 * 3. 流关闭操作
 */
@DisplayName("InternetOperator 网络操作器测试")
@ExtendWith(MockitoExtension.class)
class InternetOperatorTest {

    private SecureSocket originalHookSocket;

    @Mock
    private SecureSocket mockSecureSocket;

    @Mock
    private Socket mockSocket;

    @Mock
    private Closeable mockCloseable;

    @BeforeEach
    void setUp() {
        originalHookSocket = NeoLink.hookSocket;
    }

    @AfterEach
    void tearDown() throws IOException {
        NeoLink.hookSocket = originalHookSocket;
    }

    @Test
    @DisplayName("sendStr 应调用 hookSocket.sendStr")
    void testSendStr() throws IOException {
        NeoLink.hookSocket = mockSecureSocket;

        InternetOperator.sendStr("test message");

        verify(mockSecureSocket).sendStr("test message");
    }

    @Test
    @DisplayName("receiveStr 应调用 hookSocket.receiveStr")
    void testReceiveStr() throws IOException {
        NeoLink.hookSocket = mockSecureSocket;
        when(mockSecureSocket.receiveStr()).thenReturn("response");

        String result = InternetOperator.receiveStr();

        assertEquals("response", result);
        verify(mockSecureSocket).receiveStr();
    }

    @Test
    @DisplayName("close 应关闭所有非空 Closeable")
    void testCloseMultipleCloseables() throws IOException {
        Closeable closeable1 = mock(Closeable.class);
        Closeable closeable2 = mock(Closeable.class);

        InternetOperator.close(closeable1, closeable2, null);

        verify(closeable1).close();
        verify(closeable2).close();
    }

    @Test
    @DisplayName("close 应忽略 null 参数")
    void testCloseWithNull() {
        assertDoesNotThrow(() -> InternetOperator.close(null, null, null));
    }

    @Test
    @DisplayName("close 应忽略关闭时的异常")
    void testCloseIgnoresException() throws IOException {
        Closeable closeable = mock(Closeable.class);
        doThrow(new IOException("Test exception")).when(closeable).close();

        assertDoesNotThrow(() -> InternetOperator.close(closeable));
    }

    @Test
    @DisplayName("shutdownInput(SecureSocket) 应调用 socket.shutdownInput")
    void testShutdownInputSecureSocket() throws IOException {
        InternetOperator.shutdownInput(mockSecureSocket);

        verify(mockSecureSocket).shutdownInput();
    }

    @Test
    @DisplayName("shutdownInput(Socket) 应调用 socket.shutdownInput")
    void testShutdownInputSocket() throws IOException {
        InternetOperator.shutdownInput(mockSocket);

        verify(mockSocket).shutdownInput();
    }

    @Test
    @DisplayName("shutdownOutput(SecureSocket) 应调用 socket.shutdownOutput")
    void testShutdownOutputSecureSocket() throws IOException {
        InternetOperator.shutdownOutput(mockSecureSocket);

        verify(mockSecureSocket).shutdownOutput();
    }

    @Test
    @DisplayName("shutdownOutput(Socket) 应调用 socket.shutdownOutput")
    void testShutdownOutputSocket() throws IOException {
        InternetOperator.shutdownOutput(mockSocket);

        verify(mockSocket).shutdownOutput();
    }

    @Test
    @DisplayName("shutdownInput 应忽略异常")
    void testShutdownInputIgnoresException() throws IOException {
        doThrow(new IOException("Test")).when(mockSecureSocket).shutdownInput();

        assertDoesNotThrow(() -> InternetOperator.shutdownInput(mockSecureSocket));
    }

    @Test
    @DisplayName("shutdownOutput 应忽略异常")
    void testShutdownOutputIgnoresException() throws IOException {
        doThrow(new IOException("Test")).when(mockSecureSocket).shutdownOutput();

        assertDoesNotThrow(() -> InternetOperator.shutdownOutput(mockSecureSocket));
    }

    @Test
    @DisplayName("receiveBytes 应调用 hookSocket.receiveBytes")
    void testReceiveBytes() throws IOException {
        NeoLink.hookSocket = mockSecureSocket;
        byte[] expected = new byte[]{1, 2, 3, 4, 5};
        when(mockSecureSocket.receiveBytes()).thenReturn(expected);

        byte[] result = InternetOperator.receiveBytes();

        assertArrayEquals(expected, result);
        verify(mockSecureSocket).receiveBytes();
    }

    @Test
    @DisplayName("close 应处理空数组")
    void testCloseEmptyArray() {
        assertDoesNotThrow(() -> InternetOperator.close());
    }
}
