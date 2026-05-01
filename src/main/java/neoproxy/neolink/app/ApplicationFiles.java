package neoproxy.neolink.app;

import neoproxy.neolink.NeoLink;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 应用文件定位器（application file locator）。
 *
 * <p>这里集中处理当前可执行体 / executable 的真实路径解析，避免入口类和配置类都各自处理
 * URL decode、JAR path、IDE 运行目录这些细节。</p>
 */
public final class ApplicationFiles {

    private ApplicationFiles() {
    }

    public static File currentExecutableFile() {
        try {
            String jarFilePath = NeoLink.class.getProtectionDomain().getCodeSource().getLocation().getFile();
            jarFilePath = java.net.URLDecoder.decode(jarFilePath, StandardCharsets.UTF_8);
            return new File(jarFilePath);
        } catch (Exception ignore) {
            return null;
        }
    }
}
