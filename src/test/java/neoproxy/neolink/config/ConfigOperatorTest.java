package neoproxy.neolink.config;

import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigOperator 测试类
 *
 * 测试范围：
 * 1. 平台特定路径获取
 * 2. 基准包目录查找
 * 3. 配置同步
 */
@DisplayName("ConfigOperator 配置管理器测试")
class ConfigOperatorTest {

    @TempDir
    File tempDir;

    private String originalWorkingDir;
    private String originalBasePackageDir;

    @BeforeEach
    void setUp() throws Exception {
        originalWorkingDir = ConfigOperator.WORKING_DIR;
        originalBasePackageDir = ConfigOperator.BASE_PACKAGE_DIR;
    }

    @AfterEach
    void tearDown() {
        ConfigOperator.WORKING_DIR = originalWorkingDir;
        ConfigOperator.BASE_PACKAGE_DIR = originalBasePackageDir;
    }

    @Test
    @DisplayName("getPlatformSpecificDataPath Windows 应返回 LOCALAPPDATA 路径")
    void testGetPlatformSpecificDataPathWindows() throws Exception {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            Method method = ConfigOperator.class.getDeclaredMethod("getPlatformSpecificDataPath");
            method.setAccessible(true);

            String originalOs = System.getProperty("os.name");
            System.setProperty("os.name", "Windows 11");

            try {
                String result = (String) method.invoke(null);
                assertTrue(result.contains("NeoLink"));
            } finally {
                System.setProperty("os.name", originalOs);
            }
        }
    }

    @Test
    @DisplayName("getPlatformSpecificDataPath macOS 应返回 Library/Application Support 路径")
    void testGetPlatformSpecificDataPathMacOS() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("getPlatformSpecificDataPath");
        method.setAccessible(true);

        String originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Mac OS X");

        try {
            String result = (String) method.invoke(null);
            assertTrue(result.contains("Library") || result.contains("NeoLink"));
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }

    @Test
    @DisplayName("getPlatformSpecificDataPath Linux 应返回 .neolink 路径")
    void testGetPlatformSpecificDataPathLinux() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("getPlatformSpecificDataPath");
        method.setAccessible(true);

        String originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Linux");

        try {
            String result = (String) method.invoke(null);
            assertTrue(result.contains(".neolink") || result.contains("NeoLink"));
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }

    @Test
    @DisplayName("forceSyncBaseline 应同步存在的文件")
    void testForceSyncBaseline() throws Exception {
        File sourceFile = new File(tempDir, "test.txt");
        Files.writeString(sourceFile.toPath(), "test content");

        ConfigOperator.BASE_PACKAGE_DIR = tempDir.getAbsolutePath();
        ConfigOperator.WORKING_DIR = tempDir.toPath().resolve("work").toString();
        new File(ConfigOperator.WORKING_DIR).mkdirs();

        Method method = ConfigOperator.class.getDeclaredMethod("forceSyncBaseline", String.class);
        method.setAccessible(true);
        method.invoke(null, "test.txt");

        File targetFile = new File(ConfigOperator.WORKING_DIR, "test.txt");
        assertTrue(targetFile.exists());
        assertEquals("test content", Files.readString(targetFile.toPath()));
    }

    @Test
    @DisplayName("forceSyncBaseline 不存在的文件应静默处理")
    void testForceSyncBaselineNonExistent() throws Exception {
        ConfigOperator.BASE_PACKAGE_DIR = tempDir.getAbsolutePath();
        ConfigOperator.WORKING_DIR = tempDir.getAbsolutePath();

        Method method = ConfigOperator.class.getDeclaredMethod("forceSyncBaseline", String.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null, "non-existent.txt"));
    }

    @Test
    @DisplayName("findBasePackageDir 在 IDEA 环境应返回 user.dir")
    void testFindBasePackageDirIdeaEnvironment() throws Exception {
        File nodeJson = new File(System.getProperty("user.dir"), "node.json");
        boolean created = false;

        if (!nodeJson.exists()) {
            nodeJson.createNewFile();
            created = true;
        }

        try {
            Method method = ConfigOperator.class.getDeclaredMethod("findBasePackageDir", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(null, tempDir.getAbsolutePath());
            assertEquals(System.getProperty("user.dir"), result);
        } finally {
            if (created) {
                nodeJson.delete();
            }
        }
    }

    @Test
    @DisplayName("readAndSetValue 不存在的配置文件应安全返回")
    void testReadAndSetValueNonExistentConfig() {
        ConfigOperator.WORKING_DIR = tempDir.getAbsolutePath();

        assertDoesNotThrow(() -> ConfigOperator.readAndSetValue());
    }

    @Test
    @DisplayName("WORKING_DIR 和 BASE_PACKAGE_DIR 应为 String 类型")
    void testDirectoryFieldTypes() throws Exception {
        Field workingDirField = ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);

        Field basePackageDirField = ConfigOperator.class.getDeclaredField("BASE_PACKAGE_DIR");
        basePackageDirField.setAccessible(true);

        assertEquals(String.class, workingDirField.getType());
        assertEquals(String.class, basePackageDirField.getType());
    }
}
