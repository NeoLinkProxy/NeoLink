package neoproxy.neolink.app;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 应用文件定位器。
 *
 * <p>这里集中处理当前可执行体的真实路径解析，避免入口类和配置类都各自处理
 * URL 解码、JAR 路径、IDE 运行目录这些细节。</p>
 *
 * <p>跨平台适配：使用 {@code ApplicationFiles.class} 自身的 CodeSource 定位 JAR，
 * 不再依赖入口类 NeoLink.class，使得 common 模块可独立编译。</p>
 */
public final class ApplicationFiles {

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
}
