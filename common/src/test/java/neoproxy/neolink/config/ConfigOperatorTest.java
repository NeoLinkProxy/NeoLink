package neoproxy.neolink.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `ConfigOperatorTest` 回归测试。
 */
@DisplayName("ConfigOperatorTest")
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
        ConfigOperator.setWorkingDirectoryProvider(null);
    }

    @Test
    @DisplayName("testGetPlatformSpecificDataPathWindows")
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
    @DisplayName("testGetPlatformSpecificDataPathMacOS")
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
    @DisplayName("testGetPlatformSpecificDataPathLinux")
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
    @DisplayName("testForceSyncBaseline")
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
    @DisplayName("testForceSyncBaselineNonExistent")
    void testForceSyncBaselineNonExistent() throws Exception {
        ConfigOperator.BASE_PACKAGE_DIR = tempDir.getAbsolutePath();
        ConfigOperator.WORKING_DIR = tempDir.getAbsolutePath();

        Method method = ConfigOperator.class.getDeclaredMethod("forceSyncBaseline", String.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null, "non-existent.txt"));
    }

    @Test
    @DisplayName("initEnvironment with provider bootstraps desktop config from packaged template")
    void initEnvironmentWithProviderBootstrapsConfigTemplate() {
        ConfigOperator.setWorkingDirectoryProvider(() -> tempDir.toPath());

        ConfigOperator.initEnvironment();

        File configFile = new File(tempDir, "config.cfg");
        assertTrue(configFile.isFile());
        assertDoesNotThrow(() -> {
            LineConfigParser parser = new LineConfigParser(configFile);
            parser.load();
            assertTrue(parser.getOptional("NAS_URL").isPresent());
            assertTrue(parser.getOptional("NKM_NODELIST_URL").isPresent());
        });
    }

    @Test
    @DisplayName("testFindBasePackageDirIdeaEnvironment")
    void testFindBasePackageDirIdeaEnvironment() throws Exception {
        File nodeJson = new File(System.getProperty("user.dir"), NodeConfig.NODE_LIST_FILE_NAME);
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
    @DisplayName("testFindBasePackageDirIgnoresDeprecatedNodeJson")
    void testFindBasePackageDirIgnoresDeprecatedNodeJson() throws Exception {
        File legacyUserDir = new File(tempDir, "legacy-user-dir");
        File programDir = new File(tempDir, "program-dir");
        assertTrue(legacyUserDir.mkdirs());
        assertTrue(programDir.mkdirs());
        Files.writeString(new File(legacyUserDir, "node.json").toPath(), "[]");

        Method method = ConfigOperator.class.getDeclaredMethod("findBasePackageDir", String.class);
        method.setAccessible(true);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", legacyUserDir.getAbsolutePath());
        try {
            String result = (String) method.invoke(null, programDir.getAbsolutePath());
            assertEquals(programDir.getAbsolutePath(), result);
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("testReadAndSetValueNonExistentConfig")
    void testReadAndSetValueNonExistentConfig() {
        ConfigOperator.WORKING_DIR = tempDir.getAbsolutePath();

        assertDoesNotThrow(() -> ConfigOperator.readAndSetValue());
    }

    @Test
    @DisplayName("testDirectoryFieldTypes")
    void testDirectoryFieldTypes() throws Exception {
        Field workingDirField = ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);

        Field basePackageDirField = ConfigOperator.class.getDeclaredField("BASE_PACKAGE_DIR");
        basePackageDirField.setAccessible(true);

        assertEquals(String.class, workingDirField.getType());
        assertEquals(String.class, basePackageDirField.getType());
    }

    @Test
    @DisplayName("testResolveWritableRuntimeDirectoryPrefersWorkingDir")
    void testResolveWritableRuntimeDirectoryPrefersWorkingDir() {
        ConfigOperator.BASE_PACKAGE_DIR = tempDir.toPath().resolve("base").toString();
        ConfigOperator.WORKING_DIR = tempDir.toPath().resolve("working").toString();

        File result = ConfigOperator.resolveWritableRuntimeDirectory();

        assertEquals(new File(ConfigOperator.WORKING_DIR).getAbsoluteFile(), result);
    }
}
