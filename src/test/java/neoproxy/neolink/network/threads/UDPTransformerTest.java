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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UDPTransformer 测试类
 *
 * 测试范围：
 * 1. 数据包序列化/反序列化
 * 2. 构造函数初始化
 * 3. 模式常量验证
 * 4. run 方法行为
 * 5. 缓冲区初始化
 */
@DisplayName("UDPTransformer UDP转发器测试")
class UDPTransformerTest {

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
        var field = UDPTransformer.class.getDeclaredField("MODE_NEO_TO_LOCAL");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(0, value);
    }

    @Test
    @DisplayName("MODE_LOCAL_TO_NEO 常量应为 1")
    void testModeLocalToNeoConstant() throws Exception {
        var field = UDPTransformer.class.getDeclaredField("MODE_LOCAL_TO_NEO");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(1, value);
    }

    @Test
    @DisplayName("BUFFER_LENGTH 应为 65535")
    void testBufferLengthConstant() throws Exception {
        var field = UDPTransformer.class.getDeclaredField("BUFFER_LENGTH");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(65535, value);
    }

    @Test
    @DisplayName("构造函数应正确初始化 MODE_NEO_TO_LOCAL 模式")
    void testConstructorNeoToLocalMode() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        Field modeField = UDPTransformer.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        int mode = modeField.getInt(transformer);
        assertEquals(0, mode);
    }

    @Test
    @DisplayName("构造函数应正确初始化 MODE_LOCAL_TO_NEO 模式")
    void testConstructorLocalToNeoMode() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                DatagramSocket.class, SecureSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        Field modeField = UDPTransformer.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        int mode = modeField.getInt(transformer);
        assertEquals(1, mode);
    }

    @Test
    @DisplayName("构造函数应正确初始化接收缓冲区")
    void testConstructorReceiveBufferInitialization() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        Field bufferField = UDPTransformer.class.getDeclaredField("receiveBuffer");
        bufferField.setAccessible(true);
        byte[] buffer = (byte[]) bufferField.get(transformer);
        assertNotNull(buffer);
        assertEquals(65535, buffer.length);
    }

    @Test
    @DisplayName("构造函数应正确初始化序列化缓冲区")
    void testConstructorSerializationBufferInitialization() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        Field bufferField = UDPTransformer.class.getDeclaredField("serializationBuffer");
        bufferField.setAccessible(true);
        java.nio.ByteBuffer buffer = (java.nio.ByteBuffer) bufferField.get(transformer);
        assertNotNull(buffer);
        assertEquals(65560, buffer.capacity());
    }

    @Test
    @DisplayName("run 方法在 null socket 时应安全结束")
    void testRunWithNullSockets() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        assertDoesNotThrow(() -> transformer.run());
    }

    @Test
    @DisplayName("run 方法在 MODE_LOCAL_TO_NEO 时应安全结束")
    void testRunWithLocalToNeoMode() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                DatagramSocket.class, SecureSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        assertDoesNotThrow(() -> transformer.run());
    }

    private Object createTransformerInstance() throws Exception {
        Constructor<?>[] constructors = UDPTransformer.class.getDeclaredConstructors();
        Constructor<?> constructor = constructors[0];
        constructor.setAccessible(true);
        return constructor.newInstance(null, null);
    }

    @Test
    @DisplayName("deserializeToDatagramPacket 应正确反序列化有效数据")
    void testDeserializeToDatagramPacketValidData() throws Exception {
        byte[] testData = "Hello UDP".getBytes();
        InetAddress testAddress = InetAddress.getByName("127.0.0.1");
        int testPort = 12345;

        DatagramPacket originalPacket = new DatagramPacket(testData, testData.length, testAddress, testPort);

        Object instance = createTransformerInstance();
        Method serializeMethod = UDPTransformer.class.getDeclaredMethod("serializeDatagramPacket", DatagramPacket.class);
        serializeMethod.setAccessible(true);

        byte[] serialized = (byte[]) serializeMethod.invoke(instance, originalPacket);

        DatagramPacket deserialized = UDPTransformer.deserializeToDatagramPacket(serialized);

        assertNotNull(deserialized);
        assertEquals(testData.length, deserialized.getLength());
        assertEquals(testPort, deserialized.getPort());
        assertArrayEquals(testData, deserialized.getData());
    }

    @Test
    @DisplayName("deserializeToDatagramPacket 应拒绝无效魔数")
    void testDeserializeToDatagramPacketInvalidMagic() {
        byte[] invalidData = new byte[20];
        for (int i = 0; i < 20; i++) {
            invalidData[i] = (byte) i;
        }

        assertThrows(IllegalArgumentException.class, () -> {
            UDPTransformer.deserializeToDatagramPacket(invalidData);
        });
    }

    @Test
    @DisplayName("deserializeToDatagramPacket 应处理 IPv6 地址")
    void testDeserializeToDatagramPacketIPv6() throws Exception {
        byte[] testData = "IPv6 Test".getBytes();
        InetAddress testAddress = InetAddress.getByName("::1");
        int testPort = 54321;

        DatagramPacket originalPacket = new DatagramPacket(testData, testData.length, testAddress, testPort);

        Object instance = createTransformerInstance();
        Method serializeMethod = UDPTransformer.class.getDeclaredMethod("serializeDatagramPacket", DatagramPacket.class);
        serializeMethod.setAccessible(true);

        byte[] serialized = (byte[]) serializeMethod.invoke(instance, originalPacket);

        DatagramPacket deserialized = UDPTransformer.deserializeToDatagramPacket(serialized);

        assertNotNull(deserialized);
        assertEquals(testData.length, deserialized.getLength());
        assertEquals(testPort, deserialized.getPort());
        assertArrayEquals(testData, deserialized.getData());
    }

    @Test
    @DisplayName("序列化数据应包含正确的魔数")
    void testSerializedDataContainsMagicNumber() throws Exception {
        byte[] testData = "Magic Test".getBytes();
        InetAddress testAddress = InetAddress.getByName("192.168.1.1");
        int testPort = 8080;

        DatagramPacket packet = new DatagramPacket(testData, testData.length, testAddress, testPort);

        Object instance = createTransformerInstance();
        Method serializeMethod = UDPTransformer.class.getDeclaredMethod("serializeDatagramPacket", DatagramPacket.class);
        serializeMethod.setAccessible(true);

        byte[] serialized = (byte[]) serializeMethod.invoke(instance, packet);

        assertNotNull(serialized);
        assertTrue(serialized.length >= 4);

        int magic = ((serialized[0] & 0xFF) << 24) |
                ((serialized[1] & 0xFF) << 16) |
                ((serialized[2] & 0xFF) << 8) |
                (serialized[3] & 0xFF);

        assertEquals(0xDEADBEEF, magic);
    }

    @Test
    @DisplayName("序列化后反序列化应保持数据一致性")
    void testSerializeDeserializeConsistency() throws Exception {
        byte[] testData = new byte[1000];
        for (int i = 0; i < 1000; i++) {
            testData[i] = (byte) (i % 256);
        }
        InetAddress testAddress = InetAddress.getByName("10.0.0.1");
        int testPort = 9999;

        DatagramPacket originalPacket = new DatagramPacket(testData, testData.length, testAddress, testPort);

        Object instance = createTransformerInstance();
        Method serializeMethod = UDPTransformer.class.getDeclaredMethod("serializeDatagramPacket", DatagramPacket.class);
        serializeMethod.setAccessible(true);

        byte[] serialized = (byte[]) serializeMethod.invoke(instance, originalPacket);
        DatagramPacket deserialized = UDPTransformer.deserializeToDatagramPacket(serialized);

        assertNotNull(deserialized);
        assertEquals(originalPacket.getLength(), deserialized.getLength());
        assertEquals(originalPacket.getPort(), deserialized.getPort());
        assertArrayEquals(originalPacket.getData(), deserialized.getData());
    }

    @Test
    @DisplayName("空数据包序列化应正常工作")
    void testSerializeEmptyPacket() throws Exception {
        byte[] testData = new byte[0];
        InetAddress testAddress = InetAddress.getByName("127.0.0.1");
        int testPort = 12345;

        DatagramPacket packet = new DatagramPacket(testData, 0, testAddress, testPort);

        Object instance = createTransformerInstance();
        Method serializeMethod = UDPTransformer.class.getDeclaredMethod("serializeDatagramPacket", DatagramPacket.class);
        serializeMethod.setAccessible(true);

        byte[] serialized = (byte[]) serializeMethod.invoke(instance, packet);
        DatagramPacket deserialized = UDPTransformer.deserializeToDatagramPacket(serialized);

        assertNotNull(deserialized);
        assertEquals(0, deserialized.getLength());
    }

    @Test
    @DisplayName("run 方法应实现 Runnable 接口")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(UDPTransformer.class));
    }

    @Test
    @DisplayName("plainSocket 字段应正确初始化")
    void testPlainSocketField() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        Field plainSocketField = UDPTransformer.class.getDeclaredField("plainSocket");
        plainSocketField.setAccessible(true);
        DatagramSocket socket = (DatagramSocket) plainSocketField.get(transformer);
        assertNull(socket);
    }

    @Test
    @DisplayName("secureSocket 字段应正确初始化")
    void testSecureSocketField() throws Exception {
        Constructor<?> constructor = UDPTransformer.class.getDeclaredConstructor(
                SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);

        UDPTransformer transformer = (UDPTransformer) constructor.newInstance(null, null);

        Field secureSocketField = UDPTransformer.class.getDeclaredField("secureSocket");
        secureSocketField.setAccessible(true);
        SecureSocket socket = (SecureSocket) secureSocketField.get(transformer);
        assertNull(socket);
    }
}
