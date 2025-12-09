package neoproxy.neolink;

import neoproxy.neolink.threads.CheckAliveThread;
import neoproxy.neolink.threads.TCPTransformer;
import plethora.utils.config.LineConfigReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class ConfigOperator {

    public static final File CONFIG_FILE = new File(NeoLink.CURRENT_DIR_PATH + File.separator + "config.cfg");
    public static final File NODE_FILE = new File(NeoLink.CURRENT_DIR_PATH + File.separator + "node.json");

    private static final Path CONFIG_PATH = CONFIG_FILE.toPath();
    private static final Path NODE_PATH = NODE_FILE.toPath();

    private ConfigOperator() {
        // 工具类，禁止实例化
    }

    /**
     * 读取配置文件并设置所有相关的静态变量。
     * 同时也检查 node11.json 是否存在。
     */
    public static void readAndSetValue() {
        // 1. 检查并创建 config.cfg (从资源模板复制)
        if (!Files.exists(CONFIG_PATH)) {
            copyResourceToFile("/templates/config.cfg", CONFIG_FILE);
        }

        // 2. 检查 node11.json，如果不存在则创建一个空的数组 "[]"
        if (!Files.exists(NODE_PATH)) {
            createEmptyNodeJson();
        }

        LineConfigReader reader = new LineConfigReader(CONFIG_PATH.toFile());

        try {
            reader.load();
            applySettings(reader);
        } catch (IOException e) {
            NeoLink.say("配置文件可能已损坏，正在重新创建默认配置并重试...");
            copyResourceToFile("/templates/config.cfg", CONFIG_FILE);
            try {
                reader.load();
                applySettings(reader);
            } catch (IOException fatalException) {
                NeoLink.say("致命错误：无法加载配置文件，程序无法启动。");
                fatalException.printStackTrace();
                System.exit(-1);
            }
        }
    }

    private static void applySettings(LineConfigReader reader) {
        NeoLink.remoteDomainName = reader.getOptional("REMOTE_DOMAIN_NAME").orElse("localhost");
        NeoLink.localDomainName = reader.getOptional("LOCAL_DOMAIN_NAME").orElse("localhost");
        NeoLink.hostHookPort = reader.getOptional("HOST_HOOK_PORT").map(Integer::parseInt).orElse(44801);
        NeoLink.hostConnectPort = reader.getOptional("HOST_CONNECT_PORT").map(Integer::parseInt).orElse(44802);
        NeoLink.enableAutoReconnect = reader.getOptional("ENABLE_AUTO_RECONNECT").map(Boolean::parseBoolean).orElse(true);
        NeoLink.enableAutoUpdate = reader.getOptional("ENABLE_AUTO_UPDATE").map(Boolean::parseBoolean).orElse(true);
        NeoLink.reconnectionIntervalSeconds = reader.getOptional("RECONNECTION_INTERVAL").map(Integer::parseInt).orElse(30);
        NeoLink.enableProxyProtocol = reader.getOptional("ENABLE_PROXY_PROTOCOL").map(Boolean::parseBoolean).orElse(false);

        ProxyOperator.PROXY_IP_TO_NEO_SERVER = reader.getOptional("PROXY_IP_TO_NEO_SERVER").orElse("");
        ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = reader.getOptional("PROXY_IP_TO_LOCAL_SERVER").orElse("");
        CheckAliveThread.HEARTBEAT_PACKET_DELAY = reader.getOptional("HEARTBEAT_PACKET_DELAY").map(Integer::parseInt).orElse(1000);
        TCPTransformer.BUFFER_LENGTH = reader.getOptional("BUFFER_LEN").map(Integer::parseInt).orElse(4096);
    }

    /**
     * 从资源文件夹复制模板文件到本地磁盘
     */
    private static void copyResourceToFile(String resourcePath, File destination) {
        try (InputStream stream = ConfigOperator.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                NeoLink.say("错误：无法在Jar包中找到资源文件: " + resourcePath);
                destination.createNewFile();
                return;
            }
            Files.copy(stream, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            NeoLink.say("已生成默认文件: " + destination.getName());
        } catch (IOException e) {
            NeoLink.say("写入文件失败: " + destination.getName());
            e.printStackTrace();
        }
    }

    /**
     * 创建一个包含空数组 [] 的 node11.json
     */
    private static void createEmptyNodeJson() {
        try {
            Files.writeString(NODE_PATH, "[]", StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            NeoLink.say("创建 node11.json 失败: " + e.getMessage());
        }
    }
}