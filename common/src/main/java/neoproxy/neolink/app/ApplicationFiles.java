package neoproxy.neolink.app;

import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.NodeConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 应用文件定位器。
 *
 * <p>这里集中处理当前可执行体的真实路径解析，避免入口类和配置类都各自处理
 * URL 解码、JAR 路径、IDE 运行目录这些细节。</p>
 *
 * <p>运行时文件也必须通过这里定位。业务模块只声明“我要配置 / 状态 / 缓存 / 日志”，
 * 不再自行拼接 {@code WORKING_DIR}，这样目录分层和权限回退只有一个真源。</p>
 *
 * <p>跨平台适配：使用 {@code ApplicationFiles.class} 自身的 CodeSource 定位 JAR，
 * 不再依赖入口类 NeoLink.class，使得 common 模块可独立编译。</p>
 */
public final class ApplicationFiles {
    private static final String CONFIG_FILE_NAME = "config.cfg";
    private static final String SESSION_FILE_NAME = "desktop-session.json";
    private static final String TUNNELS_FILE_NAME = "tunnels.json";
    private static final String LOCK_FILE_NAME = "neolink-desktop.lock";
    private static final String EULA_FILE_NAME = "eula.txt";

    private ApplicationFiles() {
    }

    /**
     * 获取当前可执行文件（JAR / classes 目录）的真实路径。
     * <p>
     * 该方法通过 CodeSource 自解析，不依赖特定入口类。
     * 如果解析失败（如在 Android 或特殊类加载器环境下），返回 null。
     */
    public static File currentExecutableFile() {
        try {
            String jarFilePath = ApplicationFiles.class.getProtectionDomain().getCodeSource().getLocation().getFile();
            jarFilePath = java.net.URLDecoder.decode(jarFilePath, StandardCharsets.UTF_8);
            return new File(jarFilePath);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static File runtimeRoot() {
        return ConfigOperator.resolveWritableRuntimeDirectory();
    }

    public static File configDir() {
        return child(runtimeRoot(), "config");
    }

    public static File stateDir() {
        return child(runtimeRoot(), "state");
    }

    public static File cacheDir() {
        return child(runtimeRoot(), "cache");
    }

    public static File logsDir() {
        return child(runtimeRoot(), "logs");
    }

    public static File uiLogsDir() {
        return child(logsDir(), "ui");
    }

    public static File tunnelLogsDir() {
        return child(logsDir(), "tunnels");
    }

    public static File lockDir() {
        return child(runtimeRoot(), "lock");
    }

    public static File updatesDir() {
        return child(runtimeRoot(), "updates");
    }

    public static File configFile() {
        return child(configDir(), CONFIG_FILE_NAME);
    }

    public static File sessionFile() {
        return child(stateDir(), SESSION_FILE_NAME);
    }

    public static File tunnelsFile() {
        return child(stateDir(), TUNNELS_FILE_NAME);
    }

    public static File nodesCacheFile() {
        return child(cacheDir(), NodeConfig.NODE_LIST_FILE_NAME);
    }

    public static File lockFile() {
        return child(lockDir(), LOCK_FILE_NAME);
    }

    public static File eulaFile() {
        return child(runtimeRoot(), EULA_FILE_NAME);
    }

    public static File logFile(String fileName) {
        return child(logsDir(), fileName);
    }

    public static File uiLogFile(String fileName) {
        return child(uiLogsDir(), fileName);
    }

    public static File tunnelLogFile(String fileName) {
        return child(tunnelLogsDir(), fileName);
    }

    public static File resolveLogFile(String configuredOutputFilePath, String defaultFileName) {
        if (configuredOutputFilePath == null || configuredOutputFilePath.isBlank()) {
            return logFile(defaultFileName);
        }

        File configuredFile = new File(configuredOutputFilePath.trim());
        if (configuredFile.isAbsolute()) {
            return configuredFile;
        }
        return child(runtimeRoot(), configuredFile.getPath());
    }

    private static File child(File parent, String name) {
        return new File(parent, name).getAbsoluteFile();
    }
}
