package neoproxy.neolink.config;

import fun.ceroxe.api.utils.config.LineConfigReader;
import neoproxy.neolink.core.NeoLink;
import neoproxy.neolink.network.ProxyOperator;
import neoproxy.neolink.network.threads.CheckAliveThread;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * 配置管理器
 *
 * 核心职责：
 * 1. 检测并初始化应用程序运行环境
 * 2. 确定工作目录（可写目录优先，否则重定向到系统数据目录）
 * 3. 读取并解析配置文件 config.cfg
 * 4. 同步安装包中的配置文件到用户数据目录
 *
 * 设计特点：
 * - 智能目录检测：支持 IDEA 开发环境、Windows 安装版、macOS 安装版
 * - 自动权限处理：检测到只读环境时自动重定向到用户数据目录
 * - 配置同步：确保用户数据目录有最新的配置文件
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
public final class ConfigOperator {
    public static String WORKING_DIR;
    public static String BASE_PACKAGE_DIR;

    public static void initEnvironment() {
        File currentFile = NeoLink.getCurrentFile();
        String programDir = (currentFile != null) ? currentFile.getParent() : System.getProperty("user.dir");

        // 探测基准资源位置
        BASE_PACKAGE_DIR = findBasePackageDir(programDir);
        debugOperation("Base resources path: " + BASE_PACKAGE_DIR);

        File testFile = new File(BASE_PACKAGE_DIR, ".write_test");
        try {
            if (testFile.createNewFile()) {
                testFile.delete();
                WORKING_DIR = BASE_PACKAGE_DIR; // 可写（IDEA/绿色版）
            } else {
                throw new IOException();
            }
        } catch (IOException e) {
            // 只读（安装版），重定向到 AppData
            WORKING_DIR = getPlatformSpecificDataPath();
            new File(WORKING_DIR, "logs").mkdirs();

            // 强制同步安装包里的文件到 AppData
            forceSyncBaseline("config.cfg");
            forceSyncBaseline("node.json");
            debugOperation("Redirected to AppData: " + WORKING_DIR);
        }
    }

    private static String findBasePackageDir(String programDir) {
        // 1. IDEA 项目根目录
        if (new File(System.getProperty("user.dir"), "node.json").exists())
            return System.getProperty("user.dir");

        // 2. Windows 安装目录或其 app 子目录
        if (new File(programDir, "node.json").exists()) return programDir;
        File s2 = new File(programDir + File.separator + "app", "node.json");
        if (s2.exists()) return s2.getParent();

        // 3. macOS Resources 目录
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            File s3 = new File(programDir + "/../Resources/node.json");
            if (s3.exists()) return s3.getParent();
        }
        return programDir;
    }

    private static void forceSyncBaseline(String fileName) {
        File source = new File(BASE_PACKAGE_DIR, fileName);
        if (source.exists()) {
            try {
                Files.copy(source.toPath(), new File(WORKING_DIR, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    public static void readAndSetValue() {
        File configFile = new File(WORKING_DIR, "config.cfg");
        if (!configFile.exists()) return;

        LineConfigReader reader = new LineConfigReader(configFile);
        try {
            reader.load();
            NeoLink.remoteDomainName = reader.getOptional("REMOTE_DOMAIN_NAME").orElse("localhost");
            NeoLink.localDomainName = reader.getOptional("LOCAL_DOMAIN_NAME").orElse("localhost");
            NeoLink.hostHookPort = readPort(reader, "HOST_HOOK_PORT", NodeConfig.DEFAULT_HOST_HOOK_PORT);
            NeoLink.hostConnectPort = readPort(reader, "HOST_CONNECT_PORT", NodeConfig.DEFAULT_HOST_CONNECT_PORT);
            NeoLink.enableAutoReconnect = reader.getOptional("ENABLE_AUTO_RECONNECT").map(Boolean::parseBoolean).orElse(true);
            NeoLink.enableAutoUpdate = reader.getOptional("ENABLE_AUTO_UPDATE").map(Boolean::parseBoolean).orElse(true);
            NeoLink.reconnectionIntervalSeconds = readPositiveInt(reader, "RECONNECTION_INTERVAL", 30);
            NeoLink.enableProxyProtocol = reader.getOptional("ENABLE_PROXY_PROTOCOL").map(Boolean::parseBoolean).orElse(false);
            NeoLink.nkmNodeListUrl = reader.getOptional("NKM_NODELIST_URL").orElse("");
            ProxyOperator.PROXY_IP_TO_NEO_SERVER = reader.getOptional("PROXY_IP_TO_NEO_SERVER").orElse("");
            ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = reader.getOptional("PROXY_IP_TO_LOCAL_SERVER").orElse("");
            CheckAliveThread.HEARTBEAT_PACKET_DELAY = readPositiveInt(reader, "HEARTBEAT_PACKET_DELAY", 1000);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config.cfg: " + e.getMessage(), e);
        }
    }

    private static int readPort(LineConfigReader reader, String key, int defaultValue) {
        return reader.getOptional(key)
                .filter(value -> !value.isBlank())
                .map(value -> parseIntegerInRange(key, value, 1, 65535))
                .orElse(defaultValue);
    }

    private static int readPositiveInt(LineConfigReader reader, String key, int defaultValue) {
        return reader.getOptional(key)
                .filter(value -> !value.isBlank())
                .map(value -> parseIntegerInRange(key, value, 1, Integer.MAX_VALUE))
                .orElse(defaultValue);
    }

    private static int parseIntegerInRange(String key, String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ".");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer.", e);
        }
    }

    private static String getPlatformSpecificDataPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) return System.getenv("LOCALAPPDATA") + File.separator + "NeoLink";
        if (os.contains("mac")) return home + "/Library/Application Support/NeoLink";
        return home + File.separator + ".neolink";
    }
}
