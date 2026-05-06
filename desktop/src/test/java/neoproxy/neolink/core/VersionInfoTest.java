package neoproxy.neolink.core;

import neoproxy.neolink.config.ConfigOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `VersionInfo` 回归测试。
 */
@DisplayName("VersionInfoTest")
class VersionInfoTest {
    private static final Pattern BUILD_SCRIPT_VERSION_PATTERN =
            Pattern.compile("(?m)^\\s*extra\\[\"neoLinkApiVersion\"\\]\\s*=\\s*\"([^\"]+)\"");

    @TempDir
    File tempDir;

    private String originalUserDir;
    private String originalWorkingDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        originalWorkingDir = ConfigOperator.WORKING_DIR;
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir);
        ConfigOperator.WORKING_DIR = originalWorkingDir;
    }

    private void useEulaDirectory(File directory) {
        System.setProperty("user.dir", directory.getAbsolutePath());
        ConfigOperator.WORKING_DIR = directory.getAbsolutePath();
    }

    private String versionFromBuildScript() throws IOException {
        String content = Files.readString(Path.of("..", "build.gradle.kts"), StandardCharsets.UTF_8);
        Matcher matcher = BUILD_SCRIPT_VERSION_PATTERN.matcher(content);
        assertTrue(matcher.find(), "build.gradle.kts must declare neoLinkApiVersion");
        return matcher.group(1);
    }

    @Test
    @DisplayName("testVersionIsResolvedFromBuildScript")
    void testVersionIsResolvedFromBuildScript() throws Exception {
        assertNotNull(VersionInfo.VERSION);
        assertFalse(VersionInfo.VERSION.isEmpty());
        assertEquals(versionFromBuildScript(), VersionInfo.VERSION);
    }

    @Test
    @DisplayName("testVersionNotPlaceholder")
    void testVersionNotPlaceholder() {
        assertNotEquals("${version}", VersionInfo.VERSION);
    }

    @Test
    @DisplayName("testAuthorConstant")
    void testAuthorConstant() {
        assertEquals("Ceroxe", VersionInfo.AUTHOR);
    }

    @Test
    @DisplayName("testOutPutEulaUsesOfficialProductMetadata")
    void testOutPutEulaUsesOfficialProductMetadata() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        String content = Files.readString(new File(tempDir, "eula.txt").toPath());
        assertTrue(content.contains("NeoLink"));
        assertTrue(content.contains("Ceroxe"));
        assertFalse(content.contains("[Software Name]"));
        assertFalse(content.contains("[Your Developer/Company Name Here]"));
        assertFalse(content.contains("__NEOLINK_VERSION__"));
    }

    @Test
    @DisplayName("testOutPutEulaCreatesFile")
    void testOutPutEulaCreatesFile() {
        useEulaDirectory(tempDir);

        File eulaFile = new File(tempDir, "eula.txt");
        assertFalse(eulaFile.exists());

        VersionInfo.outPutEula();

        assertTrue(eulaFile.exists());
    }

    @Test
    @DisplayName("testOutPutEulaUsesWorkingDir")
    void testOutPutEulaUsesWorkingDir() {
        File userDir = new File(tempDir, "user-dir");
        File workingDir = new File(tempDir, "working-dir");
        assertTrue(userDir.mkdirs());
        assertTrue(workingDir.mkdirs());
        System.setProperty("user.dir", userDir.getAbsolutePath());
        ConfigOperator.WORKING_DIR = workingDir.getAbsolutePath();

        VersionInfo.outPutEula();

        assertFalse(new File(userDir, "eula.txt").exists());
        assertTrue(new File(workingDir, "eula.txt").isFile());
    }

    @Test
    @DisplayName("testOutPutEulaContainsChineseContent")
    void testOutPutEulaContainsChineseContent() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("最终用户许可协议（EULA）"));
        assertTrue(content.contains("NeoLink"));
    }

    @Test
    @DisplayName("testOutPutEulaContainsEnglishContent")
    void testOutPutEulaContainsEnglishContent() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("最终用户许可协议"));
    }

    @Test
    @DisplayName("testOutPutEulaOverwritesExisting")
    void testOutPutEulaOverwritesExisting() throws Exception {
        useEulaDirectory(tempDir);

        File eulaFile = new File(tempDir, "eula.txt");
        Files.writeString(eulaFile.toPath(), "Old content");

        VersionInfo.outPutEula();

        String content = Files.readString(eulaFile.toPath());
        assertTrue(content.contains("NeoLink"));
        assertFalse(content.contains("Old content"));
    }

    @Test
    @DisplayName("testEulaContainsResolvedVersion")
    void testEulaContainsResolvedVersion() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("版本：" + VersionInfo.VERSION));
        assertFalse(content.contains("__NEOLINK_VERSION__"));
    }

    @Test
    @DisplayName("testEulaContainsEffectiveDate")
    void testEulaContainsEffectiveDate() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("生效日期"));
    }

    @Test
    @DisplayName("testEulaContainsIntellectualProperty")
    void testEulaContainsIntellectualProperty() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("知识产权"));
    }
}
