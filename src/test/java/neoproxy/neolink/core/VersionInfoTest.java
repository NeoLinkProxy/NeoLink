package neoproxy.neolink.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VersionInfo 测试类
 *
 * 测试范围：
 * 1. 版本号获取
 * 2. 作者信息
 * 3. EULA 文件生成
 */
@DisplayName("VersionInfo 版本信息测试")
class VersionInfoTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("VERSION 应为非空字符串")
    void testVersionIsNotEmpty() {
        assertNotNull(VersionInfo.VERSION);
        assertFalse(VersionInfo.VERSION.isEmpty());
        assertEquals("7.0.0", VersionInfo.VERSION);
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
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            File eulaFile = new File(tempDir, "eula.txt");
            assertFalse(eulaFile.exists());

            VersionInfo.outPutEula();

            assertTrue(eulaFile.exists());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("outPutEula 文件应包含中文 EULA")
    void testOutPutEulaContainsChineseContent() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            VersionInfo.outPutEula();

            File eulaFile = new File(tempDir, "eula.txt");
            String content = Files.readString(eulaFile.toPath());

            assertTrue(content.contains("最终用户许可协议"));
            assertTrue(content.contains("NeoLink"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("outPutEula 文件应包含英文 EULA")
    void testOutPutEulaContainsEnglishContent() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            VersionInfo.outPutEula();

            File eulaFile = new File(tempDir, "eula.txt");
            String content = Files.readString(eulaFile.toPath());

            assertTrue(content.contains("End-User License Agreement"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("outPutEula 应覆盖已存在的文件")
    void testOutPutEulaOverwritesExisting() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            File eulaFile = new File(tempDir, "eula.txt");
            Files.writeString(eulaFile.toPath(), "Old content");

            VersionInfo.outPutEula();

            String content = Files.readString(eulaFile.toPath());
            assertTrue(content.contains("最终用户许可协议"));
            assertFalse(content.contains("Old content"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("EULA 文件应包含版本信息")
    void testEulaContainsVersion() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            VersionInfo.outPutEula();

            File eulaFile = new File(tempDir, "eula.txt");
            String content = Files.readString(eulaFile.toPath());

            assertTrue(content.contains("版本：1.0") || content.contains("Version: 1.0"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("EULA 文件应包含生效日期")
    void testEulaContainsEffectiveDate() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            VersionInfo.outPutEula();

            File eulaFile = new File(tempDir, "eula.txt");
            String content = Files.readString(eulaFile.toPath());

            assertTrue(content.contains("生效日期") || content.contains("Effective Date"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("EULA 文件应包含知识产权条款")
    void testEulaContainsIntellectualProperty() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            VersionInfo.outPutEula();

            File eulaFile = new File(tempDir, "eula.txt");
            String content = Files.readString(eulaFile.toPath());

            assertTrue(content.contains("知识产权") || content.contains("Intellectual Property"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}
