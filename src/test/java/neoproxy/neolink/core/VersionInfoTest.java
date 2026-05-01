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
 * VersionInfo 测试类
 * <p>
 * 测试范围：
 * 1. 版本号获取
 * 2. 作者信息
 * 3. EULA 文件生成
 */
@DisplayName("VersionInfo 版本信息测试")
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
    @DisplayName("VERSION 应为非空字符串")
    void testVersionIsNotEmpty() {
        assertNotNull(VersionInfo.VERSION);
        assertFalse(VersionInfo.VERSION.isEmpty());
        assertEquals("7.1.0", VersionInfo.VERSION);
    }

    @Test
    @DisplayName("VERSION 不应是原始占位符")
    void testVersionNotPlaceholder() {
        assertNotEquals("${version}", VersionInfo.VERSION);
    }

    @Test
    @DisplayName("AUTHOR 应为 Ceroxe")
    void testAuthorConstant() {
        assertEquals("Ceroxe", VersionInfo.AUTHOR);
    }

    @Test
    @DisplayName("outPutEula 应创建 EULA 文件")
    void testOutPutEulaCreatesFile() throws Exception {
        useEulaDirectory(tempDir);

        File eulaFile = new File(tempDir, "eula.txt");
        assertFalse(eulaFile.exists());

        VersionInfo.outPutEula();

        assertTrue(eulaFile.exists());
    }

    @Test
    @DisplayName("outPutEula 应写入工作目录而不是启动目录")
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
    @DisplayName("outPutEula 文件应包含中文 EULA")
    void testOutPutEulaContainsChineseContent() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("最终用户许可协议"));
        assertTrue(content.contains("NeoLink"));
    }

    @Test
    @DisplayName("outPutEula 文件应包含英文 EULA")
    void testOutPutEulaContainsEnglishContent() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("End-User License Agreement"));
    }

    @Test
    @DisplayName("outPutEula 应覆盖已存在的文件")
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
    @DisplayName("EULA 文件应包含版本信息")
    void testEulaContainsVersion() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("版本：1.0") || content.contains("Version: 1.0"));
    }

    @Test
    @DisplayName("EULA 文件应包含生效日期")
    void testEulaContainsEffectiveDate() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("生效日期") || content.contains("Effective Date"));
    }

    @Test
    @DisplayName("EULA 文件应包含知识产权条款")
    void testEulaContainsIntellectualProperty() throws Exception {
        useEulaDirectory(tempDir);

        VersionInfo.outPutEula();

        File eulaFile = new File(tempDir, "eula.txt");
        String content = Files.readString(eulaFile.toPath());

        assertTrue(content.contains("知识产权") || content.contains("Intellectual Property"));
    }
}
