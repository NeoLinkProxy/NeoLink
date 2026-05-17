package neoproxy.neolink.config;

import neoproxy.neolink.state.ConnectionSettings;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureSettings;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.app.ApplicationFiles;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * 配置引导与配置加载器。
 *
 * <p>设计原因：
 * 环境探测必须产出一个所有子系统都能写入的运行时目录。把这个解析过程集中在这里，
 * 可以避免在安装目录只读时，更新文件、配置文件、日志和 EULA 输出分别落到不同位置。</p>
 *
 * <p>跨平台适配：
 * 工作目录的探测逻辑通过 {@link WorkingDirectoryProvider} 接口注入。
 * 默认使用桌面端策略（AppData/Library/.neolink），可通过
 * {@link #setWorkingDirectoryProvider(WorkingDirectoryProvider)} 替换为 Android 等实现。</p>
 */
public final class ConfigOperator {
    public static String WORKING_DIR;
    public static String BASE_PACKAGE_DIR;

    /**
     * 可配置的工作目录提供者。默认为 null 时使用内置的桌面端探测策略。
     */
    private static volatile WorkingDirectoryProvider workingDirectoryProvider;

    private ConfigOperator() {
    }

    /**
     * 注入自定义的工作目录提供者（Android 等平台使用）。
     * 必须在 {@link #initEnvironment()} 之前调用。
     */
    public static void setWorkingDirectoryProvider(WorkingDirectoryProvider provider) {
        workingDirectoryProvider = provider;
    }

    public static void initEnvironment() {
        // 优先使用注入的 provider（Android 场景）
        if (workingDirectoryProvider != null) {
            Path resolved = workingDirectoryProvider.resolveWorkingDirectory();
            WORKING_DIR = resolved.toAbsolutePath().toString();
            BASE_PACKAGE_DIR = WORKING_DIR;
            ensureDirectory(resolved);
            ensureConfigTemplateExists();
            debugOperation("WorkingDirectoryProvider resolved: " + WORKING_DIR);
            return;
        }

        // 桌面端默认策略：探测可执行体所在目录
        String programDir = System.getProperty("user.dir");

        BASE_PACKAGE_DIR = findBasePackageDir(programDir);
        debugOperation("Base resources path: " + BASE_PACKAGE_DIR);

        File basePackageDir = safeDirectory(BASE_PACKAGE_DIR);
        if (basePackageDir != null && isWritableDirectory(basePackageDir)) {
            WORKING_DIR = basePackageDir.getAbsolutePath();
            ensureConfigTemplateExists();
            return;
        }

        File workingDirectory = new File(getPlatformSpecificDataPath());
        WORKING_DIR = workingDirectory.getAbsolutePath();
        ensureDirectory(workingDirectory.toPath());
        forceSyncBaseline("config.cfg");
        forceSyncBaseline(NodeConfig.NODE_LIST_FILE_NAME);
        ensureConfigTemplateExists();
        debugOperation("Redirected to AppData: " + WORKING_DIR);
    }

    public static File resolveWritableRuntimeDirectory() {
        File workingDirectory = safeDirectory(WORKING_DIR);
        if (workingDirectory != null) {
            return workingDirectory;
        }

        File basePackageDirectory = safeDirectory(BASE_PACKAGE_DIR);
        if (basePackageDirectory != null) {
            return basePackageDirectory;
        }

        return new File(System.getProperty("user.dir")).getAbsoluteFile();
    }

    private static boolean isWritableDirectory(File directory) {
        if (directory == null) {
            return false;
        }
        try {
            Files.createDirectories(directory.toPath());
            File probeFile = File.createTempFile("neolink-write-test-", ".tmp", directory);
            Files.deleteIfExists(probeFile.toPath());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create NeoLink runtime directory: " + directory, e);
        }
    }

    private static File safeDirectory(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return new File(path).getAbsoluteFile();
    }

    private static String findBasePackageDir(String programDir) {
        if (new File(System.getProperty("user.dir"), NodeConfig.NODE_LIST_FILE_NAME).exists()) {
            return System.getProperty("user.dir");
        }

        if (new File(programDir, NodeConfig.NODE_LIST_FILE_NAME).exists()) {
            return programDir;
        }
        File packagedAppDir = new File(programDir + File.separator + "app", NodeConfig.NODE_LIST_FILE_NAME);
        if (packagedAppDir.exists()) {
            return packagedAppDir.getParent();
        }

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            File macResourcesDir = new File(programDir + "/../Resources/" + NodeConfig.NODE_LIST_FILE_NAME);
            if (macResourcesDir.exists()) {
                return macResourcesDir.getParent();
            }
        }
        return programDir;
    }

    private static void forceSyncBaseline(String fileName) {
        File source = new File(BASE_PACKAGE_DIR, fileName);
        if (!source.exists()) {
            return;
        }
        try {
            File target = baselineTarget(fileName);
            File parent = target.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.copy(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            debugOperation(e);
        }
    }

    private static File baselineTarget(String fileName) {
        if ("config.cfg".equals(fileName)) {
            return ApplicationFiles.configFile();
        }
        if (NodeConfig.NODE_LIST_FILE_NAME.equals(fileName)) {
            return ApplicationFiles.nodesCacheFile();
        }
        return new File(ApplicationFiles.runtimeRoot(), fileName).getAbsoluteFile();
    }

    private static void ensureConfigTemplateExists() {
        File target = ApplicationFiles.configFile();
        if (target.exists()) {
            return;
        }
        try (InputStream template = ConfigOperator.class.getClassLoader().getResourceAsStream("templates/config.cfg")) {
            if (template == null) {
                debugOperation("templates/config.cfg not found on classpath; skipped desktop config bootstrap.");
                return;
            }
            File parent = target.getAbsoluteFile().getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.copy(template, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            debugOperation(e);
        }
    }

    public static void readAndSetValue() {
        File configFile = ApplicationFiles.configFile();
        if (!configFile.exists()) {
            return;
        }

        LineConfigParser reader = new LineConfigParser(configFile);
        try {
            reader.load();
            ConnectionSettings currentConnection = ConnectionState.snapshot();
            FeatureSettings currentFeatures = FeatureState.snapshot();
            ConnectionState.apply(new ConnectionSettings(
                    reader.getOptional("REMOTE_DOMAIN_NAME").orElse("localhost"),
                    reader.getOptional("LOCAL_DOMAIN_NAME").orElse("localhost"),
                    readPort(reader, "HOST_HOOK_PORT", NodeConfig.DEFAULT_HOST_HOOK_PORT),
                    readPort(reader, "HOST_CONNECT_PORT", NodeConfig.DEFAULT_HOST_CONNECT_PORT),
                    currentConnection.key(),
                    currentConnection.localPort(),
                    currentConnection.specifiedNodeName()
            ));
            FeatureState.apply(new FeatureSettings(
                    currentFeatures.debugMode(),
                    currentFeatures.showConnection(),
                    currentFeatures.guiMode(),
                    currentFeatures.disableTcp(),
                    currentFeatures.disableUdp(),
                    reader.getOptional("ENABLE_PROXY_PROTOCOL").map(Boolean::parseBoolean).orElse(false),
                    reader.getOptional("ENABLE_AUTO_RECONNECT").map(Boolean::parseBoolean).orElse(true),
                    reader.getOptional("ENABLE_AUTO_UPDATE").map(Boolean::parseBoolean).orElse(true),
                    currentFeatures.testUpdate(),
                    currentFeatures.noEffectMode(),
                    readPositiveInt(reader, "HEARTBEAT_PACKET_DELAY", 1000),
                    readPositiveInt(reader, "RECONNECTION_INTERVAL", 30),
                    reader.getOptional("PROXY_IP_TO_LOCAL_SERVER").orElse(""),
                    reader.getOptional("PROXY_IP_TO_NEO_SERVER").orElse(""),
                    currentFeatures.outputFilePath(),
                    reader.getOptional("NKM_NODELIST_URL").orElse("")
            ));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config.cfg: " + e.getMessage(), e);
        }
    }

    private static int readPort(LineConfigParser reader, String key, int defaultValue) {
        return reader.getOptional(key)
                .filter(value -> !value.isBlank())
                .map(value -> parseIntegerInRange(key, value, 1, 65535))
                .orElse(defaultValue);
    }

    private static int readPositiveInt(LineConfigParser reader, String key, int defaultValue) {
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
        return WorkingDirectoryProvider.resolveDefaultDesktopWorkingDirectory().toString();
    }
}
