package neoproxy.neolink.update;

import com.sun.net.httpserver.HttpServer;
import neoproxy.neolink.NeoLink;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

/**
 * `UpdateManagerTest` 回归测试。
 */
@DisplayName("UpdateManagerTest")
class UpdateManagerTest {

    @TempDir
    Path tempDir;
    private boolean originalIsDebugMode;
    private LanguageData originalLanguageData;
    private boolean originalIsGUIMode;
    private String originalKey;
    private int originalLocalPort;
    private String originalOutputFilePath;
    private String originalBasePackageDir;
    private String originalWorkingDir;

    @BeforeEach
    void setUp() {
        originalIsDebugMode = FeatureState.snapshot().debugMode();
        originalLanguageData = RuntimeState.languageData();
        originalIsGUIMode = FeatureState.snapshot().guiMode();
        originalKey = ConnectionState.snapshot().key();
        originalLocalPort = ConnectionState.snapshot().localPort();
        originalOutputFilePath = FeatureState.snapshot().outputFilePath();
        originalBasePackageDir = ConfigOperator.BASE_PACKAGE_DIR;
        originalWorkingDir = ConfigOperator.WORKING_DIR;

        FeatureState.setDebugMode(false);
        RuntimeState.setLanguageData(new LanguageData());
        FeatureState.setGuiMode(false);
        ConnectionState.setKey("test-key");
        ConnectionState.setLocalPort(8080);
        FeatureState.setOutputFilePath(null);
        ConfigOperator.BASE_PACKAGE_DIR = tempDir.toString();
        ConfigOperator.WORKING_DIR = tempDir.resolve("working").toString();
    }

    @AfterEach
    void tearDown() {
        FeatureState.setDebugMode(originalIsDebugMode);
        RuntimeState.setLanguageData(originalLanguageData);
        FeatureState.setGuiMode(originalIsGUIMode);
        ConnectionState.setKey(originalKey);
        ConnectionState.setLocalPort(originalLocalPort);
        FeatureState.setOutputFilePath(originalOutputFilePath);
        ConfigOperator.BASE_PACKAGE_DIR = originalBasePackageDir;
        ConfigOperator.WORKING_DIR = originalWorkingDir;
    }

    @Test
    @DisplayName("testFormatFileSizeBytes")
    void testFormatFileSizeBytes() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 512L);
        assertEquals("512 B", result);
    }

    @Test
    @DisplayName("testFormatFileSizeKB")
    void testFormatFileSizeKB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 2048L);
        assertEquals("2 KB", result);
    }

    @Test
    @DisplayName("testFormatFileSizeMB")
    void testFormatFileSizeMB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1572864L);
        assertTrue(result.contains("MB"));
        assertTrue(result.startsWith("1.5"));
    }

    @Test
    @DisplayName("testFormatFileSizeGB")
    void testFormatFileSizeGB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 2147483648L);
        assertTrue(result.contains("GB"));
        assertTrue(result.startsWith("2.0"));
    }

    @Test
    @DisplayName("testFormatFileSizeZero")
    void testFormatFileSizeZero() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 0L);
        assertEquals("0 B", result);
    }

    @Test
    @DisplayName("testFormatFileSizeOne")
    void testFormatFileSizeOne() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1L);
        assertEquals("1 B", result);
    }

    @Test
    @DisplayName("testFormatFileSizeBoundary")
    void testFormatFileSizeBoundary() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1023L);
        assertEquals("1023 B", result);
    }

    @Test
    @DisplayName("testFormatFileSizeExactly1KB")
    void testFormatFileSizeExactly1KB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1024L);
        assertEquals("1 KB", result);
    }

    @Test
    @DisplayName("testFormatFileSizeLargeFile")
    void testFormatFileSizeLargeFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 10737418240L);
        assertTrue(result.contains("GB"));
        assertTrue(result.startsWith("10.0"));
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryNull")
    void testDeleteFileOrDirectoryNull() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null, (File) null));
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryNonExistent")
    void testDeleteFileOrDirectoryNonExistent() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        File nonExistent = new File("/non/existent/file.txt");
        assertDoesNotThrow(() -> method.invoke(null, nonExistent));
    }

    @Test
    @DisplayName("testDeleteFileOrDirectorySingleFile")
    void testDeleteFileOrDirectorySingleFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        File file = tempDir.resolve("test.txt").toFile();
        file.createNewFile();
        assertTrue(file.exists());

        method.invoke(null, file);
        assertFalse(file.exists());
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryDirectory")
    void testDeleteFileOrDirectoryDirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        File file1 = subDir.resolve("file1.txt").toFile();
        File file2 = subDir.resolve("file2.txt").toFile();
        file1.createNewFile();
        file2.createNewFile();

        assertTrue(subDir.toFile().exists());

        method.invoke(null, subDir.toFile());
        assertFalse(subDir.toFile().exists());
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryNestedDirectories")
    void testDeleteFileOrDirectoryNestedDirectories() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        Path level1 = tempDir.resolve("level1");
        Path level2 = level1.resolve("level2");
        Path level3 = level2.resolve("level3");
        Files.createDirectories(level3);

        File file = level3.resolve("file.txt").toFile();
        file.createNewFile();

        method.invoke(null, level1.toFile());
        assertFalse(level1.toFile().exists());
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryDebugMode")
    void testDeleteFileOrDirectoryDebugMode() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);
        FeatureState.setDebugMode(true);

        File file = tempDir.resolve("debug_test.txt").toFile();
        file.createNewFile();

        assertDoesNotThrow(() -> method.invoke(null, file));
        assertFalse(file.exists());
    }

    @Test
    @DisplayName("testResolveUpdateDirectoryUsesUpdatesDir")
    void testResolveUpdateDirectoryUsesUpdatesDir() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("resolveUpdateDirectory");
        method.setAccessible(true);

        File value = (File) method.invoke(null);

        assertEquals(tempDir.resolve("working").resolve("updates").toFile().getAbsoluteFile(), value.getAbsoluteFile());
    }

    @Test
    @DisplayName("testDownloadFileFromUrlInvalidUrl")
    void testDownloadFileFromUrlInvalidUrl() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("downloadFileFromUrl", String.class, File.class);
        method.setAccessible(true);

        File outputFile = tempDir.resolve("output.txt").toFile();

        File result = (File) method.invoke(null, "not-a-valid-url", outputFile);
        assertNull(result);
    }

    @Test
    @DisplayName("testDownloadFileFromUrlDownloadsInstallerExe")
    void testDownloadFileFromUrlDownloadsInstallerExe() throws Exception {
        byte[] installerBytes = "MZ fake installer payload".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/NeoLink-latest.exe", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, installerBytes.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(installerBytes);
            }
        });
        server.start();

        try {
            Method method = UpdateManager.class.getDeclaredMethod("downloadFileFromUrl", String.class, File.class);
            method.setAccessible(true);

            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/NeoLink-latest.exe";
            File fallbackOutputFile = tempDir.resolve("NeoLink-6.0.X.exe").toFile();
            File expectedOutputFile = tempDir.resolve("NeoLink-latest.exe").toFile();

            File result = (File) method.invoke(null, url, fallbackOutputFile);

            assertEquals(expectedOutputFile.getAbsoluteFile(), result);
            assertArrayEquals(installerBytes, Files.readAllBytes(result.toPath()));
            assertFalse(fallbackOutputFile.exists());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("testDownloadFileFromUrlUsesContentDispositionFileName")
    void testDownloadFileFromUrlUsesContentDispositionFileName() throws Exception {
        byte[] installerBytes = "MZ named installer payload".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/win", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"NeoLink-6.0.0-Windows-amd64-installer.exe\"");
            exchange.sendResponseHeaders(200, installerBytes.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(installerBytes);
            }
        });
        server.start();

        try {
            Method method = UpdateManager.class.getDeclaredMethod("downloadFileFromUrl", String.class, File.class);
            method.setAccessible(true);

            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/win";
            File fallbackOutputFile = tempDir.resolve("NeoLink-6.0.X.exe").toFile();
            File expectedOutputFile = tempDir.resolve("NeoLink-6.0.0-Windows-amd64-installer.exe").toFile();

            File result = (File) method.invoke(null, url, fallbackOutputFile);

            assertEquals(expectedOutputFile.getAbsoluteFile(), result);
            assertArrayEquals(installerBytes, Files.readAllBytes(result.toPath()));
            assertFalse(fallbackOutputFile.exists());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("testDownloadFileFromUrlFollowsMovedPermanentlyRedirect")
    void testDownloadFileFromUrlFollowsMovedPermanentlyRedirect() throws Exception {
        byte[] installerBytes = "MZ redirected installer payload".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/win", exchange -> {
            exchange.getResponseHeaders().set("Location", "/NeoLink-latest.exe");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/NeoLink-latest.exe", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, installerBytes.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(installerBytes);
            }
        });
        server.start();

        try {
            Method method = UpdateManager.class.getDeclaredMethod("downloadFileFromUrl", String.class, File.class);
            method.setAccessible(true);

            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/win";
            File fallbackOutputFile = tempDir.resolve("NeoLink-6.0.X.exe").toFile();
            File expectedOutputFile = tempDir.resolve("NeoLink-latest.exe").toFile();

            File result = (File) method.invoke(null, url, fallbackOutputFile);

            assertEquals(expectedOutputFile.getAbsoluteFile(), result);
            assertArrayEquals(installerBytes, Files.readAllBytes(result.toPath()));
            assertFalse(fallbackOutputFile.exists());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("testDownloadFileFromUrlNonExistentDomain")
    void testDownloadFileFromUrlNonExistentDomain() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("downloadFileFromUrl", String.class, File.class);
        method.setAccessible(true);

        File outputFile = tempDir.resolve("output.txt").toFile();

        File result = (File) method.invoke(null, "http://non-existent-domain-12345.com/file.txt", outputFile);
        assertNull(result);
    }

    @Test
    @DisplayName("testStartInstallerNullFile")
    void testStartInstallerNullFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("startInstaller", File.class);
        method.setAccessible(true);

        Boolean result = (Boolean) method.invoke(null, (File) null);

        assertFalse(result);
    }

    @Test
    @DisplayName("testStartInstallerNonExistentFile")
    void testStartInstallerNonExistentFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("startInstaller", File.class);
        method.setAccessible(true);

        File nonExistent = new File(tempDir.toFile(), "non-existent.exe");

        Boolean result = (Boolean) method.invoke(null, nonExistent);

        assertFalse(result);
    }

    @Test
    @DisplayName("testStartInstallerDirectory")
    void testStartInstallerDirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("startInstaller", File.class);
        method.setAccessible(true);

        File directory = tempDir.toFile();

        Boolean result = (Boolean) method.invoke(null, directory);

        assertFalse(result);
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryReadOnlyFile")
    void testDeleteFileOrDirectoryReadOnlyFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        File file = tempDir.resolve("readonly.txt").toFile();
        file.createNewFile();
        file.setReadOnly();

        assertDoesNotThrow(() -> method.invoke(null, file));
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryWithReadOnlyFile")
    void testDeleteFileOrDirectoryWithReadOnlyFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        Path subDir = tempDir.resolve("readonlydir");
        Files.createDirectories(subDir);
        File readOnlyFile = subDir.resolve("readonly.txt").toFile();
        readOnlyFile.createNewFile();
        readOnlyFile.setReadOnly();

        assertDoesNotThrow(() -> method.invoke(null, subDir.toFile()));
    }

    @Test
    @DisplayName("testFormatFileSizeNegative")
    void testFormatFileSizeNegative() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, -1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("testDeleteFileOrDirectoryListFilesNull")
    void testDeleteFileOrDirectoryListFilesNull() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.isDirectory()).thenReturn(true);
        when(mockFile.listFiles()).thenReturn(null);
        when(mockFile.delete()).thenReturn(true);

        assertDoesNotThrow(() -> method.invoke(null, mockFile));
    }
}
