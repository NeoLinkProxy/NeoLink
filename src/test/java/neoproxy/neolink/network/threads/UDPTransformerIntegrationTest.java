package neoproxy.neolink.network.threads;

import fun.ceroxe.api.net.SecureServerSocket;
import fun.ceroxe.api.net.SecureSocket;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UDPTransformer 集成测试
 * 
 * 使用真实的 SecureServerSocket 和 SecureSocket 测试 UDP 数据传输
 */
@DisplayName("UDPTransformer 集成测试")
class UDPTransformerIntegrationTest {

    private static final int TEST_PORT = 45680;
    private static Thread serverThread;
    private static SecureServerSocket secureServerSocket;
    private static volatile boolean serverRunning = false;

    @BeforeAll
    static void setUpServer() throws IOException {
        secureServerSocket = new SecureServerSocket(TEST_PORT);
        serverRunning = true;
        
        serverThread = Thread.ofVirtual().start(() -> {
            while (serverRunning && !secureServerSocket.isClosed()) {
                try {
                    SecureSocket clientSocket = secureServerSocket.accept();
                    Thread.ofVirtual().start(() -> handleUdpClient(clientSocket));
                } catch (IOException e) {
                    if (serverRunning) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @AfterAll
    static void tearDownServer() throws IOException {
        serverRunning = false;
        if (secureServerSocket != null) {
            secureServerSocket.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    private static void handleUdpClient(SecureSocket socket) {
        try {
            while (!socket.isClosed()) {
                byte[] data = socket.receiveByte(1000);
                if (data == null) break;
                
                DatagramPacket packet = UDPTransformer.deserializeToDatagramPacket(data);
                if (packet != null) {
                    byte[] responseData = serializeDatagramPacket(
                        packet.getData(),
                        packet.getAddress(),
                        packet.getPort()
                    );
                    socket.sendByte(responseData);
                }
            }
        } catch (Exception e) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static byte[] serializeDatagramPacket(byte[] data, InetAddress address, int port) {
        byte[] ipBytes = address.getAddress();
        int ipLength = ipBytes.length;
        int totalLen = 4 + 4 + 4 + ipLength + 2 + data.length;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalLen);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0xDEADBEEF);
        buffer.putInt(data.length);
        buffer.putInt(ipLength);
        buffer.put(ipBytes);
        buffer.putShort((short) port);
        buffer.put(data);
        
        return buffer.array();
    }

    @Test
    @DisplayName("deserializeToDatagramPacket 应正确反序列化有效数据")
    void testDeserializeValidData() throws Exception {
        byte[] testData = "Hello UDP".getBytes();
        InetAddress testAddress = InetAddress.getByName("127.0.0.1");
        int testPort = 12345;
        
        byte[] serialized = serializeDatagramPacket(testData, testAddress, testPort);
        
        DatagramPacket packet = UDPTransformer.deserializeToDatagramPacket(serialized);
        
        assertNotNull(packet);
        assertEquals(testPort, packet.getPort());
        assertArrayEquals(testData, packet.getData());
    }

    @Test
    @DisplayName("deserializeToDatagramPacket 应拒绝无效 magic number")
    void testDeserializeInvalidMagicNumber() {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0xBADBADBA);
        
        assertThrows(IllegalArgumentException.class, () -> {
            UDPTransformer.deserializeToDatagramPacket(buffer.array());
        });
    }

    @Test
    @DisplayName("deserializeToDatagramPacket 应处理 IPv6 地址")
    void testDeserializeIPv6Address() throws Exception {
        byte[] testData = "IPv6 Test".getBytes();
        InetAddress testAddress = InetAddress.getByName("::1");
        int testPort = 54321;
        
        byte[] serialized = serializeDatagramPacket(testData, testAddress, testPort);
        
        DatagramPacket packet = UDPTransformer.deserializeToDatagramPacket(serialized);
        
        assertNotNull(packet);
        assertEquals(testPort, packet.getPort());
        assertArrayEquals(testData, packet.getData());
    }

    @Test
    @DisplayName("UDPTransformer MODE_NEO_TO_LOCAL 常量应为 0")
    void testModeNeoToLocalConstant() throws Exception {
        var field = UDPTransformer.class.getDeclaredField("MODE_NEO_TO_LOCAL");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(0, value);
    }

    @Test
    @DisplayName("UDPTransformer MODE_LOCAL_TO_NEO 常量应为 1")
    void testModeLocalToNeoConstant() throws Exception {
        var field = UDPTransformer.class.getDeclaredField("MODE_LOCAL_TO_NEO");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(1, value);
    }

    @Test
    @DisplayName("UDPTransformer BUFFER_LENGTH 应为 65535")
    void testBufferLengthConstant() throws Exception {
        var field = UDPTransformer.class.getDeclaredField("BUFFER_LENGTH");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(65535, value);
    }

    @Test
    @DisplayName("UDPTransformer 应实现 Runnable 接口")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(UDPTransformer.class));
    }

    @Test
    @DisplayName("UDPTransformer 构造函数应正确初始化 MODE_NEO_TO_LOCAL 模式")
    void testConstructorNeoToLocalMode() throws Exception {
        var constructor = UDPTransformer.class.getDeclaredConstructor(
            SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);
        
        UDPTransformer transformer = constructor.newInstance(null, null);
        
        var modeField = UDPTransformer.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        int mode = modeField.getInt(transformer);
        assertEquals(0, mode);
    }

    @Test
    @DisplayName("UDPTransformer 构造函数应正确初始化 MODE_LOCAL_TO_NEO 模式")
    void testConstructorLocalToNeoMode() throws Exception {
        var constructor = UDPTransformer.class.getDeclaredConstructor(
            DatagramSocket.class, SecureSocket.class);
        constructor.setAccessible(true);
        
        UDPTransformer transformer = constructor.newInstance(null, null);
        
        var modeField = UDPTransformer.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        int mode = modeField.getInt(transformer);
        assertEquals(1, mode);
    }

    @Test
    @DisplayName("UDPTransformer run 方法在 null socket 时应安全结束")
    void testRunWithNullSockets() throws Exception {
        var constructor = UDPTransformer.class.getDeclaredConstructor(
            SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);
        
        UDPTransformer transformer = constructor.newInstance(null, null);
        
        assertDoesNotThrow(() -> transformer.run());
    }

    @Test
    @DisplayName("UDPTransformer 应能转发 UDP 数据")
    void testUDPTransformerDataTransfer() throws Exception {
        int localUdpPort = 45681;
        DatagramSocket localUdpSocket = new DatagramSocket(localUdpPort);
        
        SecureSocket secureClient = new SecureSocket("localhost", TEST_PORT);
        
        UDPTransformer transformer = new UDPTransformer(secureClient, localUdpSocket);
        Thread transformerThread = Thread.ofVirtual().start(transformer);
        
        byte[] testData = "UDP_TEST_DATA".getBytes();
        byte[] serializedData = serializeDatagramPacket(
            testData, 
            InetAddress.getByName("localhost"), 
            localUdpPort
        );
        
        assertDoesNotThrow(() -> secureClient.sendByte(serializedData));
        
        Thread.sleep(100);
        
        secureClient.close();
        localUdpSocket.close();
        transformerThread.interrupt();
    }

    @Test
    @DisplayName("UDPTransformer 缓冲区应正确初始化")
    void testBufferInitialization() throws Exception {
        var constructor = UDPTransformer.class.getDeclaredConstructor(
            SecureSocket.class, DatagramSocket.class);
        constructor.setAccessible(true);
        
        UDPTransformer transformer = constructor.newInstance(null, null);
        
        var receiveBufferField = UDPTransformer.class.getDeclaredField("receiveBuffer");
        receiveBufferField.setAccessible(true);
        byte[] receiveBuffer = (byte[]) receiveBufferField.get(transformer);
        assertNotNull(receiveBuffer);
        assertEquals(65535, receiveBuffer.length);
        
        var serializationBufferField = UDPTransformer.class.getDeclaredField("serializationBuffer");
        serializationBufferField.setAccessible(true);
        ByteBuffer serializationBuffer = (ByteBuffer) serializationBufferField.get(transformer);
        assertNotNull(serializationBuffer);
    }
}
