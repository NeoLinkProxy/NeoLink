package neoproxy.neolink;

import neoproxy.neolink.threads.CheckAliveThread;
import neoproxy.neolink.threads.TCPTransformer;
import plethora.utils.config.LineConfigReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ConfigOperator {

    public static final File CONFIG_FILE = new File(NeoLink.CURRENT_DIR_PATH + java.io.File.separator + "config.cfg");
    private static final Path CONFIG_PATH = CONFIG_FILE.toPath();

    /**
     * 默认配置内容，提取为常量以提高可读性。
     */
    private static final String DEFAULT_CONFIG_CONTENT = """
            #把你要连接的 NeoServer 的域名或者公网 ip 放到这里来
            #Put the domain name or public network ip of the NeoServer you want to connect to here
            REMOTE_DOMAIN_NAME=localhost
            
            #设置是否启用自动更新
            #Enable or disable automatic updates
            ENABLE_AUTO_UPDATE=true
            
            #如果你不知道以下的设置意味着什么，请你不要改变它
            #If you don't know what the following setting means, please don't change it
            LOCAL_DOMAIN_NAME=localhost
            HOST_HOOK_PORT=44801
            HOST_CONNECT_PORT=44802
            
            #设置用来连接本地服务器的代理服务器ip和端口，示例：socks->127.0.0.1:7890 如果需要登录则提供密码， 格式： ip:端口@用户名:密码   示例：socks->127.0.0.1:7890@Ceroxe;123456   如果不需要去请留空
            #Set the proxy server IP address and port to connect to the on-premises server,Example: socks->127.0.0.1:7890 Provide password if login is required, Format: type->ip:port@username:password Example: socks->127.0.0.1:7890@Ceroxe;123456   If you don't need to go, leave it blank
            PROXY_IP_TO_LOCAL_SERVER=
            
            #设置用来连接 NeoProxyServer 的代理服务器ip和端口，示例：socks->127.0.0.1:7890 如果需要登录则提供密码， 格式： ip:端口@用户名:密码   示例：socks->127.0.0.1:7890@Ceroxe;123456
            #Set the proxy server IP address and port to connect to the NeoProxyServer,Example: socks->127.0.0.1:7890 Provide password if login is required, Format: type->ip:port@username:password Example: socks->127.0.0.1:7890@Ceroxe;123456   If you don't need to go, leave it blank
            PROXY_IP_TO_NEO_SERVER=
            
            #设置发送心跳包的间隔，单位为毫秒
            #Set the interval for sending heartbeat packets, in milliseconds
            HEARTBEAT_PACKET_DELAY=1000
            
            #是否启用自动重连当服务端暂时离线的时候
            #Whether to enable automatic reconnection when the server is temporarily offline
            ENABLE_AUTO_RECONNECT=true
            
            #如果ENABLE_AUTO_RECONNECT设置为true，则将间隔多少秒后重连，单位为秒，且必须为大于0的整数
            #If ENABLE_AUTO_RECONNECT is set to true, the number of seconds after which reconnection will be made in seconds and must be an integer greater than 0
            RECONNECTION_INTERVAL=30
            
            #数据包数组的长度
            #The length of the packet array
            BUFFER_LEN=4096""";

    private ConfigOperator() {
        // 工具类，禁止实例化
    }

    /**
     * 读取配置文件并设置所有相关的静态变量。
     * 如果文件不存在或读取失败，将创建并使用默认配置。
     */
    public static void readAndSetValue() {
        LineConfigReader reader = new LineConfigReader(CONFIG_PATH.toFile());

        // 如果配置文件不存在，则先创建它
        if (!Files.exists(CONFIG_PATH)) {
            createDefaultConfigFile();
        }

        try {
            // 尝试加载配置文件
            reader.load();
            // 如果加载成功，应用配置
            applySettings(reader);
        } catch (IOException e) {
            // 如果加载失败（例如文件损坏），则覆盖创建默认配置，并重试一次
            NeoLink.say("配置文件可能已损坏，正在重新创建默认配置并重试...");
            createDefaultConfigFile();
            try {
                reader.load();
                applySettings(reader);
            } catch (IOException fatalException) {
                // 如果连默认配置都加载失败，则无法继续
                NeoLink.say("致命错误：无法加载配置文件，程序无法启动。");
                fatalException.printStackTrace();
                System.exit(-1);
            }
        }
    }

    /**
     * 从 LineConfigReader 中安全地读取配置并应用到各个类的静态字段。
     * 使用 Optional API 来避免 NPE 和 NumberFormatException。
     */
    private static void applySettings(LineConfigReader reader) {
        NeoLink.remoteDomainName = reader.getOptional("REMOTE_DOMAIN_NAME").orElse("localhost");
        NeoLink.localDomainName = reader.getOptional("LOCAL_DOMAIN_NAME").orElse("localhost");
        NeoLink.hostHookPort = reader.getOptional("HOST_HOOK_PORT").map(Integer::parseInt).orElse(44801);
        NeoLink.hostConnectPort = reader.getOptional("HOST_CONNECT_PORT").map(Integer::parseInt).orElse(44802);
        NeoLink.enableAutoReconnect = reader.getOptional("ENABLE_AUTO_RECONNECT").map(Boolean::parseBoolean).orElse(true);
        NeoLink.enableAutoUpdate = reader.getOptional("ENABLE_AUTO_UPDATE").map(Boolean::parseBoolean).orElse(true);
        NeoLink.reconnectionIntervalSeconds = reader.getOptional("RECONNECTION_INTERVAL").map(Integer::parseInt).orElse(30);
        ProxyOperator.PROXY_IP_TO_NEO_SERVER = reader.getOptional("PROXY_IP_TO_NEO_SERVER").orElse("");
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = reader.getOptional("PROXY_IP_TO_LOCAL_SERVER").orElse("");
        CheckAliveThread.HEARTBEAT_PACKET_DELAY = reader.getOptional("HEARTBEAT_PACKET_DELAY").map(Integer::parseInt).orElse(1000);
        TCPTransformer.BUFFER_LENGTH = reader.getOptional("BUFFER_LEN").map(Integer::parseInt).orElse(4096);
    }

    /**
     * 创建默认的配置文件，覆盖任何已存在的同名文件。
     * 使用现代的 NIO.2 API 和 try-with-resources 确保资源安全。
     */
    private static void createDefaultConfigFile() {
        try {
            // 使用 Files.write 提供了更原子和简洁的写文件方式
            // StandardOpenOption.CREATE, TRUNCATE_EXISTING, WRITE 确保文件被创建并覆盖
            Files.writeString(CONFIG_PATH, DEFAULT_CONFIG_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            NeoLink.say("写入默认配置文件失败。");
            e.printStackTrace();
            System.exit(-1);
        }
    }
}