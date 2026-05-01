package neoproxy.neolink.core;

import neoproxy.neolink.config.ConfigOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VersionInfoTest regression tests.
 */
@DisplayName("VersionInfoTest")
class VersionInfoTest {

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

    @Test
    @DisplayName("testVersionIsNotEmpty")
    void testVersionIsNotEmpty() {
        assertNotNull(VersionInfo.VERSION);
        assertFalse(VersionInfo.VERSION.isEmpty());
        assertEquals("7.1.2", VersionInfo.VERSION);
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
        assertFalse(content.contains("【软件名称】"));
        assertFalse(content.contains("[Software Name]"));
        assertFalse(content.contains("【请填写您的开发者/公司名称】"));
        assertFalse(content.contains("[Your Developer/Company Name Here]"));
    }

    @Test
    @DisplayName("testOutPutEulaCreatesFile")
    void testOutPutEulaCreatesFile() throws Exception {
        useEulaDirectory(tempDir);

        File eulaFile = new File(tempDir, "eula.txt");
        assertFalse(eulaFile.exists());

        VersionInfo.outPutEula();

        assertTrue(eulaFile.exists());
    }

    @Test
    @DisplayName("testOutPutEulaUsesWorkingDir")
    void testOutPutEulaUsesWorkingDir() throws Exception {
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

        assertTrue(content.contains("最终用户许可协议"));
        assertTrue(content.contains("NeoLink"));
    }

    @Test
    @DisplayName("testOutPutEulaContainsEnglishContent")
    void testOutPutEulaContainsEnglishContent() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("End-User License Agreement"));
    }

    @Test
    @DisplayName("testOutPutEulaOverwritesExisting")
    void testOutPutEulaOverwritesExisting() throws Exception {
        useEulaDirectory(tempDir);

        File eulaFile = new File(tempDir, "eula.txt");
        Files.writeString(eulaFile.toPath(), "Old content");

        VersionInfo.outPutEula();

        String content = Files.readString(eulaFile.toPath());
        assertTrue(content.contains("最终用户许可协议"));
        assertFalse(content.contains("Old content"));
    }

    @Test
    @DisplayName("testEulaContainsVersion")
    void testEulaContainsVersion() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("鐗堟湰锛?.0") || content.contains("Version: 1.0"));
    }

    @Test
    @DisplayName("testEulaContainsEffectiveDate")
    void testEulaContainsEffectiveDate() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("生效日期") || content.contains("Effective Date"));
    }

    @Test
    @DisplayName("testEulaContainsIntellectualProperty")
    void testEulaContainsIntellectualProperty() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("鐭ヨ瘑浜ф潈") || content.contains("Intellectual Property"));
    }
}
