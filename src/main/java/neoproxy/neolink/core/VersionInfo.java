package neoproxy.neolink.core;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.app.ApplicationFiles;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * 版本元数据与 EULA 写入器。
 *
 * <p>设计原因：
 * 打包后的应用应当信任资源过滤后的版本号，但开发与测试运行仍必须从同一份 Gradle
 * 真源解析 UI 版本，而不能把协议层依赖版本误当作桌面应用版本。EULA 正文作为资源文件
 * 发布，避免 Java 代码承载法律文本导致审阅与维护成本失控。</p>
 */
public final class VersionInfo {
    private static final String BUILD_SCRIPT_NAME = "build.gradle.kts";
    private static final String EULA_RESOURCE_NAME = "eula.txt";
    private static final Pattern BUILD_SCRIPT_VERSION_PATTERN =
            Pattern.compile("(?m)^\\s*(?:extra\\[\"neoLinkUiVersion\"]\\s*=|val\\s+neoLinkUiVersion\\s*=)\\s*\"([^\"]+)\"");

    public static final String VERSION = getAppVersion();
    public static final String AUTHOR = "Ceroxe";

    private VersionInfo() {
    }

    public static void outPutEula() {
        File eulaFile = ApplicationFiles.eulaFile();
        try {
            File parent = eulaFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }

            String renderedEula = eulaText();
            String currentContent = eulaFile.exists()
                    ? Files.readString(eulaFile.toPath(), StandardCharsets.UTF_8)
                    : null;
            if (!renderedEula.equals(currentContent)) {
                Files.writeString(
                        eulaFile.toPath(),
                        renderedEula,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            }
        } catch (IOException e) {
            debugOperation(e);
        }
    }

    static String eulaText() throws IOException {
        try (InputStream inputStream = NeoLink.class.getClassLoader().getResourceAsStream(EULA_RESOURCE_NAME)) {
            if (inputStream == null) {
                throw new IOException("Missing resource: " + EULA_RESOURCE_NAME);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String getAppVersion() {
        Properties properties = new Properties();
        try (InputStream inputStream = NeoLink.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (inputStream == null) {
                return versionFromBuildScriptOrFallback();
            }
            properties.load(inputStream);
        } catch (IOException e) {
            return versionFromBuildScriptOrFallback();
        }

        String version = properties.getProperty("app.version");
        if (version == null || version.isBlank() || version.contains("${")) {
            return versionFromBuildScriptOrFallback();
        }
        return version.trim();
    }

    private static String versionFromBuildScriptOrFallback() {
        String buildScriptVersion = findVersionFromBuildScript();
        return buildScriptVersion != null ? buildScriptVersion : "Dev-ver";
    }

    private static String findVersionFromBuildScript() {
        Path[] candidates = new Path[]{
                Path.of(System.getProperty("user.dir"), BUILD_SCRIPT_NAME),
                Path.of(NeoLink.CURRENT_DIR_PATH, BUILD_SCRIPT_NAME)
        };
        for (Path candidate : candidates) {
            String version = readVersionFromBuildScript(candidate);
            if (version != null) {
                return version;
            }
        }
        return null;
    }

    private static String readVersionFromBuildScript(Path buildScriptPath) {
        try {
            if (buildScriptPath == null || !Files.isRegularFile(buildScriptPath)) {
                return null;
            }
            String content = Files.readString(buildScriptPath, StandardCharsets.UTF_8);
            Matcher matcher = BUILD_SCRIPT_VERSION_PATTERN.matcher(content);
            if (!matcher.find()) {
                return null;
            }
            String version = matcher.group(1);
            return version == null || version.isBlank() ? null : version.trim();
        } catch (IOException e) {
            return null;
        }
    }
}
