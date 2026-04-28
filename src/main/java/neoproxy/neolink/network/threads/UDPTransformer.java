package neoproxy.neolink.network.threads;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.core.NeoLink;
import neoproxy.neolink.network.InternetOperator;
import neoproxy.neolink.util.Debugger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static neoproxy.neolink.network.InternetOperator.close;
import static neoproxy.neolink.core.NeoLink.localDomainName;
import static neoproxy.neolink.core.NeoLink.localPort;

/**
 * UDP 数据转发器
 *
 * 核心职责：
 * 1. 在本地服务和 Neo 服务器之间双向转发 UDP 数据
 * 2. 使用 ByteBuffer 实现高效的字节操作
 * 3. 通过复用实例缓冲区减少 GC 压力
 *
 * 设计特点：
 * - 双向转发：支持 Neo 到本地、本地到 Neo 两种模式
 * - ByteBuffer 优化：使用堆外缓冲区提高 I/O 性能
 * - 缓冲区复用：每个实例使用独立缓冲区，避免频繁分配内存
 * - 优雅关闭：支持中断信号，确保资源正确释放
 *
 * 性能优化：
 * - 使用 65535 字节缓冲区，支持最大 UDP 报文
 * - ByteBuffer 直接操作字节数组，减少拷贝开销
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
public class UDPTransformer implements Runnable {
    public static final int MODE_NEO_TO_LOCAL = 0;
    public static final int MODE_LOCAL_TO_NEO = 1;
    private static final int MAGIC = 0xDEADBEEF;
    private static final int SERIALIZED_HEADER_FIXED_LENGTH = 4 + 4 + 4 + 2;
    private static final int IPV4_LENGTH = 4;
    private static final int IPV6_LENGTH = 16;
    public static int BUFFER_LENGTH = 65535; // 可以保持为静态常量

    private final DatagramSocket plainSocket;
    private final SecureSocket secureSocket;
    private final int mode;

    // 🔥【性能优化】为每个实例创建独立的、可复用的接收缓冲区
    private final byte[] receiveBuffer = new byte[BUFFER_LENGTH];

    // 🔥【性能优化】为序列化创建一个可复用的ByteBuffer
    // 注意：这个大小需要根据你的最大UDP包来设定，要足够大。
    private final ByteBuffer serializationBuffer = ByteBuffer.allocate(
            SERIALIZED_HEADER_FIXED_LENGTH + IPV6_LENGTH + BUFFER_LENGTH
    );

    /**
     * 构造函数：用于从 Neo 服务器接收数据并转发到本地服务。
     */
    public UDPTransformer(SecureSocket secureSender, DatagramSocket localReceiver) {
        this.secureSocket = secureSender;
        this.plainSocket = localReceiver;
        this.mode = MODE_NEO_TO_LOCAL;
    }

    /**
     * 构造函数：用于从本地服务接收数据并转发到 Neo 服务器。
     */
    public UDPTransformer(DatagramSocket localSender, SecureSocket secureReceiver) {
        this.plainSocket = localSender;
        this.secureSocket = secureReceiver;
        this.mode = MODE_LOCAL_TO_NEO;
    }

    /**
     * 这个方法可以保持为静态，因为它不依赖实例状态。
     */
    public static DatagramPacket deserializeToDatagramPacket(byte[] serializedData) {
        if (serializedData == null || serializedData.length < SERIALIZED_HEADER_FIXED_LENGTH + IPV4_LENGTH) {
            throw new IllegalArgumentException("Serialized UDP packet is too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(serializedData);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Invalid magic number in serialized data");
        }

        int dataLen = buffer.getInt();
        int ipLen = buffer.getInt();
        if (dataLen < 0 || dataLen > BUFFER_LENGTH) {
            throw new IllegalArgumentException("Invalid UDP data length: " + dataLen);
        }
        if (ipLen != IPV4_LENGTH && ipLen != IPV6_LENGTH) {
            throw new IllegalArgumentException("Invalid IP address length: " + ipLen);
        }

        int expectedLength = SERIALIZED_HEADER_FIXED_LENGTH + ipLen + dataLen;
        if (serializedData.length != expectedLength) {
            throw new IllegalArgumentException("Serialized UDP packet length mismatch");
        }

        byte[] ipBytes = new byte[ipLen];
        buffer.get(ipBytes);
        InetAddress address;
        try {
            address = InetAddress.getByAddress(ipBytes);
        } catch (Exception e) {
            Debugger.debugOperation(e);
            return null;
        }
        int port = buffer.getShort() & 0xFFFF;
        byte[] data = new byte[dataLen];
        buffer.get(data);

        return new DatagramPacket(data, data.length, address, port);
    }

    /**
     * 🔥【重构】改为实例方法，使用实例的 receiveBuffer。
     */
    private void transferDataToNeoServer() {
        try {
            while (true) {//用异常退出循环
                // 🔥 使用实例的 receiveBuffer
                DatagramPacket incomingPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                plainSocket.receive(incomingPacket);
                // 🔥 调用实例方法进行序列化
                byte[] serializedData = serializeDatagramPacket(incomingPacket);
                secureSocket.sendBytes(serializedData);
            }
        } catch (IOException e) {
            Debugger.debugOperation(e);
        }
    }

    /**
     * 🔥【重构】改为实例方法，使用实例的 serializationBuffer。
     */
    private byte[] serializeDatagramPacket(DatagramPacket packet) {
        // 🔥 使用前先重置缓冲区
        serializationBuffer.clear();
        serializationBuffer.order(ByteOrder.BIG_ENDIAN);

        byte[] data = packet.getData();
        int offset = packet.getOffset();
        int length = packet.getLength();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        byte[] ipBytes = address.getAddress();
        int ipLength = ipBytes.length;

        // 检查缓冲区容量是否足够，如果不够则动态分配（不推荐，但更安全）
        // 或者直接抛出异常，让调用者知道包太大
        int totalLen = 4 + 4 + 4 + ipLength + 2 + length;
        if (totalLen > serializationBuffer.capacity()) {
            throw new IllegalArgumentException("UDP packet too large for serialization buffer: " + totalLen);
        }

        serializationBuffer.putInt(MAGIC);
        serializationBuffer.putInt(length);
        serializationBuffer.putInt(ipLength);
        serializationBuffer.put(ipBytes);
        serializationBuffer.putShort((short) port);
        serializationBuffer.put(data, offset, length);

        // 🔥 返回一个副本，因为ByteBuffer的内部数组会被重用
        return Arrays.copyOf(serializationBuffer.array(), serializationBuffer.position());
    }

    /**
     * 🔥【重构】改为实例方法。
     */
    private void transferDataToLocalServer() {
        try {
            byte[] data;
            while ((data = secureSocket.receiveBytes()) != null) {
                DatagramPacket datagramPacket = deserializeToDatagramPacket(data);
                if (datagramPacket != null) {
                    DatagramPacket outgoingPacket = new DatagramPacket(
                            datagramPacket.getData(),
                            datagramPacket.getLength(),
                            InetAddress.getByName(localDomainName),
                            localPort
                    );
                    plainSocket.send(outgoingPacket);
                }
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
        }
    }

    @Override
    public void run() {
        try {
            if (mode == MODE_NEO_TO_LOCAL) {
                transferDataToLocalServer();
            } else {
                transferDataToNeoServer();
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
        } finally {
            // 最终修复：无论正常结束还是异常结束，都确保关闭资源
            close(plainSocket, secureSocket);
        }
    }
}
