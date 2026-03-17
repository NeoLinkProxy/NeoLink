package neoproxy.neolink.network.threads;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TCPTransformer 测试类
 *
 * 测试范围：
 * 1. Proxy Protocol v2 签名检测
 * 2. 构造函数初始化
 * 3. 模式常量验证
 * 4. run 方法行为
 * 5. 缓冲区初始化
 */
@DisplayName("TCPTransformer TCP转发器测试")
class TCPTransformerTest {

    private boolean originalDebugMode;

    @BeforeEach
    void setUp() {
        originalDebugMode = NeoLink.isDebugMode;
        NeoLink.isDebugMode = false;
    }

    @AfterEach
    void tearDown() {
        NeoLink.isDebugMode = originalDebugMode;
    }

    @Test
    @DisplayName("MODE_NEO_TO_LOCAL 常量应为 0")
    void testModeNeoToLocalConstant() throws Exception {
        var field = TCPTransformer.class.getDeclaredField("MODE_NEO_TO_LOCAL");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(0, value);
    }

    @Test
    @DisplayName("MODE_LOCAL_TO_NEO 常量应为 1")
    void testModeLocalToNeoConstant() throws Exception {
        var field = TCPTransformer.class.getDeclaredField("MODE_LOCAL_TO_NEO");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(1, value);
    }

    @Test
    @DisplayName("BUFFER_LENGTH 应为 65535")
    void testBufferLengthConstant() throws Exception {
        var field = TCPTransformer.class.getDeclaredField("BUFFER_LENGTH");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(65535, value);
    }

    @Test
    @DisplayName("构造函数应正确初始化 MODE_NEO_TO_LOCAL 模式")
    void testConstructorNeoToLocalMode() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, Socket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, false);

        Field modeField = TCPTransformer.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        int mode = modeField.getInt(transformer);
        assertEquals(0, mode);
    }

    @Test
    @DisplayName("构造函数应正确初始化 MODE_LOCAL_TO_NEO 模式")
    void testConstructorLocalToNeoMode() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                Socket.class, SecureSocket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, false);

        Field modeField = TCPTransformer.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        int mode = modeField.getInt(transformer);
        assertEquals(1, mode);
    }

    @Test
    @DisplayName("构造函数应正确初始化 enableProxyProtocol 标志")
    void testConstructorEnableProxyProtocol() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, Socket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, true);

        Field ppField = TCPTransformer.class.getDeclaredField("enableProxyProtocol");
        ppField.setAccessible(true);
        boolean enablePP = ppField.getBoolean(transformer);
        assertTrue(enablePP);
    }

    @Test
    @DisplayName("构造函数应正确初始化缓冲区")
    void testConstructorBufferInitialization() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, Socket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, false);

        Field bufferField = TCPTransformer.class.getDeclaredField("buffer");
        bufferField.setAccessible(true);
        byte[] buffer = (byte[]) bufferField.get(transformer);
        assertNotNull(buffer);
        assertEquals(65535, buffer.length);
    }

    @Test
    @DisplayName("run 方法在 null socket 时应安全结束")
    void testRunWithNullSockets() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, Socket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, false);

        assertDoesNotThrow(() -> transformer.run());
    }

    @Test
    @DisplayName("run 方法在 MODE_LOCAL_TO_NEO 时应安全结束")
    void testRunWithLocalToNeoMode() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                Socket.class, SecureSocket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, false);

        assertDoesNotThrow(() -> transformer.run());
    }

    private Object createTransformerInstance() throws Exception {
        Constructor<?>[] constructors = TCPTransformer.class.getDeclaredConstructors();
        Constructor<?> constructor = constructors[0];
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, false);
    }

    @Test
    @DisplayName("isProxyProtocolV2Signature 应正确识别 PPv2 签名")
    void testIsProxyProtocolV2SignatureWithValidSignature() throws Exception {
        byte[] validPpV2Header = new byte[]{
                (byte) 0x0D, (byte) 0x0A, (byte) 0x0D, (byte) 0x0A,
                (byte) 0x00, (byte) 0x0D, (byte) 0x0A, (byte) 0x51,
                (byte) 0x55, (byte) 0x49, (byte) 0x54, (byte) 0x0A,
                0x00, 0x00, 0x00, 0x00
        };

        Object instance = createTransformerInstance();
        Method method = TCPTransformer.class.getDeclaredMethod("isProxyProtocolV2Signature", byte[].class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(instance, (Object) validPpV2Header));
    }

    @Test
    @DisplayName("isProxyProtocolV2Signature 应拒绝无效签名")
    void testIsProxyProtocolV2SignatureWithInvalidSignature() throws Exception {
        byte[] invalidHeader = new byte[]{
                0x00, 0x01, 0x02, 0x03,
                0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B,
                0x0C, 0x0D, 0x0E, 0x0F
        };

        Object instance = createTransformerInstance();
        Method method = TCPTransformer.class.getDeclaredMethod("isProxyProtocolV2Signature", byte[].class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(instance, (Object) invalidHeader));
    }

    @Test
    @DisplayName("isProxyProtocolV2Signature 应处理 null 数据")
    void testIsProxyProtocolV2SignatureWithNull() throws Exception {
        Object instance = createTransformerInstance();
        Method method = TCPTransformer.class.getDeclaredMethod("isProxyProtocolV2Signature", byte[].class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(instance, (Object) null));
    }

    @Test
    @DisplayName("isProxyProtocolV2Signature 应处理短数据")
    void testIsProxyProtocolV2SignatureWithShortData() throws Exception {
        byte[] shortData = new byte[]{0x0D, 0x0A, 0x0D, 0x0A};

        Object instance = createTransformerInstance();
        Method method = TCPTransformer.class.getDeclaredMethod("isProxyProtocolV2Signature", byte[].class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(instance, (Object) shortData));
    }

    @Test
    @DisplayName("isProxyProtocolV2Signature 应处理恰好 12 字节的有效签名")
    void testIsProxyProtocolV2SignatureWithExact12Bytes() throws Exception {
        byte[] exact12Bytes = new byte[]{
                (byte) 0x0D, (byte) 0x0A, (byte) 0x0D, (byte) 0x0A,
                (byte) 0x00, (byte) 0x0D, (byte) 0x0A, (byte) 0x51,
                (byte) 0x55, (byte) 0x49, (byte) 0x54, (byte) 0x0A
        };

        Object instance = createTransformerInstance();
        Method method = TCPTransformer.class.getDeclaredMethod("isProxyProtocolV2Signature", byte[].class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(instance, (Object) exact12Bytes));
    }

    @Test
    @DisplayName("isProxyProtocolV2Signature 应拒绝部分匹配的签名")
    void testIsProxyProtocolV2SignatureWithPartialMatch() throws Exception {
        byte[] partialMatch = new byte[]{
                (byte) 0x0D, (byte) 0x0A, (byte) 0x0D, (byte) 0x0A,
                (byte) 0x00, (byte) 0x0D, (byte) 0x0A, (byte) 0x51,
                (byte) 0x55, (byte) 0x48, (byte) 0x54, (byte) 0x0A
        };

        Object instance = createTransformerInstance();
        Method method = TCPTransformer.class.getDeclaredMethod("isProxyProtocolV2Signature", byte[].class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(instance, (Object) partialMatch));
    }

    @Test
    @DisplayName("isProxyProtocolV2Signature 应拒绝空数组")
    void testIsProxyProtocolV2SignatureWithEmptyArray() throws Exception {
        byte[] emptyArray = new byte[0];

        Object instance = createTransformerInstance();
        Method method = TCPTransformer.class.getDeclaredMethod("isProxyProtocolV2Signature", byte[].class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(instance, (Object) emptyArray));
    }

    @Test
    @DisplayName("PPV2_SIG 签名应为正确的 Proxy Protocol v2 签名")
    void testPpv2SigCorrectValue() throws Exception {
        var field = TCPTransformer.class.getDeclaredField("PPV2_SIG");
        field.setAccessible(true);
        byte[] sig = (byte[]) field.get(null);

        assertEquals(12, sig.length);
        assertEquals((byte) 0x0D, sig[0]);
        assertEquals((byte) 0x0A, sig[1]);
        assertEquals((byte) 0x0D, sig[2]);
        assertEquals((byte) 0x0A, sig[3]);
        assertEquals((byte) 0x00, sig[4]);
        assertEquals((byte) 0x0D, sig[5]);
        assertEquals((byte) 0x0A, sig[6]);
        assertEquals((byte) 0x51, sig[7]);
        assertEquals((byte) 0x55, sig[8]);
        assertEquals((byte) 0x49, sig[9]);
        assertEquals((byte) 0x54, sig[10]);
        assertEquals((byte) 0x0A, sig[11]);
    }

    @Test
    @DisplayName("run 方法应实现 Runnable 接口")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(TCPTransformer.class));
    }

    @Test
    @DisplayName("plainSocket 字段应正确初始化")
    void testPlainSocketField() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, Socket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, false);

        Field plainSocketField = TCPTransformer.class.getDeclaredField("plainSocket");
        plainSocketField.setAccessible(true);
        Socket socket = (Socket) plainSocketField.get(transformer);
        assertNull(socket);
    }

    @Test
    @DisplayName("secureSocket 字段应正确初始化")
    void testSecureSocketField() throws Exception {
        Constructor<?> constructor = TCPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, Socket.class, boolean.class);
        constructor.setAccessible(true);

        TCPTransformer transformer = (TCPTransformer) constructor.newInstance(null, null, false);

        Field secureSocketField = TCPTransformer.class.getDeclaredField("secureSocket");
        secureSocketField.setAccessible(true);
        SecureSocket socket = (SecureSocket) secureSocketField.get(transformer);
        assertNull(socket);
    }
}
