package neoproject.neolink.threads;

import plethora.net.SecureSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static neoproject.neolink.NeoLink.*;

/**
 * 数据传输器，负责在本地服务和 Neo 服务器之间双向转发数据。
 */
public class UDPTransformer implements Runnable {
    public static final int MODE_NEO_TO_LOCAL = 0;
    public static final int MODE_LOCAL_TO_NEO = 1;
    public static int BUFFER_LENGTH = 4096;
    private final DatagramSocket plainSocket;
    private final SecureSocket secureSocket;
    private final int mode;

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
     * 将数据从本地服务转发到 Neo 服务器。
     */
    public static void transferDataToNeoServer(DatagramSocket localSender, SecureSocket neoReceiver) {
        try {
            while (true) {
                byte[] buffer = new byte[BUFFER_LENGTH];
                DatagramPacket incomingPacket = new DatagramPacket(buffer, buffer.length);
                localSender.receive(incomingPacket);
                neoReceiver.sendByte(serializeDatagramPacket(incomingPacket));
            }
        } catch (IOException e) {
            debugOperation(e);
        }
    }

    /**
     * 将数据从 Neo 服务器转发到本地服务。
     */
    public static void transferDataToLocalServer(SecureSocket neoSender, DatagramSocket localReceiver) {
        try {
            byte[] data;
            while ((data = neoSender.receiveByte()) != null) {
                DatagramPacket datagramPacket = deserializeToDatagramPacket(data);
                if (datagramPacket != null) {
                    DatagramPacket outgoingPacket = new DatagramPacket(
                            datagramPacket.getData(),
                            datagramPacket.getLength(),
                            InetAddress.getByName(localDomainName),
                            localPort
                    );
                    localReceiver.send(outgoingPacket);
                }
            }
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    public static byte[] serializeDatagramPacket(DatagramPacket packet) {
        byte[] data = packet.getData();
        int offset = packet.getOffset(); // 实际数据在缓冲区的起始位置
        int length = packet.getLength(); // 实际数据长度
        InetAddress address = packet.getAddress(); // 源 IP
        int port = packet.getPort(); // 源端口

        // 获取 IP 地址的字节数组 (IPv4 是 4 字节, IPv6 是 16 字节)
        byte[] ipBytes = address.getAddress();
        int ipLength = ipBytes.length;

        // 计算总长度: Magic(4) + DataLen(4) + IPLen(4) + IPBytes(n) + Port(2) + Data(len)
        int totalLen = 4 + 4 + 4 + ipLength + 2 + length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLen);
        buffer.order(ByteOrder.BIG_ENDIAN); // 使用网络字节序 (大端)

        buffer.putInt(0xDEADBEEF); // Magic number for verification
        buffer.putInt(length);     // Original data length
        buffer.putInt(ipLength);   // Length of IP address bytes
        buffer.put(ipBytes);       // IP address bytes
        buffer.putShort((short) port); // Port number
        buffer.put(data, offset, length); // Actual data from the packet

        return buffer.array();
    }

    public static DatagramPacket deserializeToDatagramPacket(byte[] serializedData) {
        ByteBuffer buffer = ByteBuffer.wrap(serializedData);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int magic = buffer.getInt();
        if (magic != 0xDEADBEEF) {
            throw new IllegalArgumentException("Invalid magic number in serialized data");
        }

        int dataLen = buffer.getInt();
        int ipLen = buffer.getInt();
        byte[] ipBytes = new byte[ipLen];
        buffer.get(ipBytes);
        InetAddress address;
        try {
            address = InetAddress.getByAddress(ipBytes);
        } catch (Exception e) {
            debugOperation(e);
            return null;
        }
        int port = buffer.getShort() & 0xFFFF; // Convert unsigned short
        byte[] data = new byte[dataLen];
        buffer.get(data);

        // 重建 DatagramPacket
        // 注意：这里创建的 packet 的 address 和 outPort 是 C 的，不是 S 的

        return new DatagramPacket(data, data.length, address, port);
    }

    @Override
    public void run() {
        if (mode == MODE_NEO_TO_LOCAL) {
            transferDataToLocalServer(secureSocket, plainSocket);
        } else {
            transferDataToNeoServer(plainSocket, secureSocket);
        }
    }
}