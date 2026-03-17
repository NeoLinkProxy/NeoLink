package neoproxy.neolink.update;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neolink.config.LanguageData;
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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UpdateManager 测试类
 *
 * 测试范围：
 * 1. 文件大小格式化
 * 2. 文件/目录删除
 * 3. 可执行文件查找
 * 4. 常量验证
 * 5. 下载文件方法
 * 6. 启动新版本方法
 */
@DisplayName("UpdateManager 更新管理器测试")
class UpdateManagerTest {

    private boolean originalIsDebugMode;
    private LanguageData originalLanguageData;
    private boolean originalIsGUIMode;
    private String originalKey;
    private int originalLocalPort;
    private String originalOutputFilePath;
    private SecureSocket originalHookSocket;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        originalIsDebugMode = NeoLink.isDebugMode;
        originalLanguageData = NeoLink.languageData;
        originalIsGUIMode = NeoLink.isGUIMode;
        originalKey = NeoLink.key;
        originalLocalPort = NeoLink.localPort;
        originalOutputFilePath = NeoLink.outputFilePath;
        originalHookSocket = NeoLink.hookSocket;

        NeoLink.isDebugMode = false;
        NeoLink.languageData = new LanguageData();
        NeoLink.isGUIMode = false;
        NeoLink.key = "test-key";
        NeoLink.localPort = 8080;
        NeoLink.outputFilePath = null;
    }

    @AfterEach
    void tearDown() {
        NeoLink.isDebugMode = originalIsDebugMode;
        NeoLink.languageData = originalLanguageData;
        NeoLink.isGUIMode = originalIsGUIMode;
        NeoLink.key = originalKey;
        NeoLink.localPort = originalLocalPort;
        NeoLink.outputFilePath = originalOutputFilePath;
        NeoLink.hookSocket = originalHookSocket;
    }

    @Test
    @DisplayName("formatFileSize 应正确格式化字节")
    void testFormatFileSizeBytes() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 512L);
        assertEquals("512 B", result);
    }

    @Test
    @DisplayName("formatFileSize 应正确格式化 KB")
    void testFormatFileSizeKB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 2048L);
        assertEquals("2 KB", result);
    }

    @Test
    @DisplayName("formatFileSize 应正确格式化 MB")
    void testFormatFileSizeMB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1572864L);
        assertTrue(result.contains("MB"));
        assertTrue(result.startsWith("1.5"));
    }

    @Test
    @DisplayName("formatFileSize 应正确格式化 GB")
    void testFormatFileSizeGB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 2147483648L);
        assertTrue(result.contains("GB"));
        assertTrue(result.startsWith("2.0"));
    }

    @Test
    @DisplayName("formatFileSize 应处理 0 字节")
    void testFormatFileSizeZero() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 0L);
        assertEquals("0 B", result);
    }

    @Test
    @DisplayName("formatFileSize 应处理 1 字节")
    void testFormatFileSizeOne() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1L);
        assertEquals("1 B", result);
    }

    @Test
    @DisplayName("formatFileSize 应处理边界值 1023 字节")
    void testFormatFileSizeBoundary() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1023L);
        assertEquals("1023 B", result);
    }

    @Test
    @DisplayName("formatFileSize 应处理刚好 1 KB")
    void testFormatFileSizeExactly1KB() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 1024L);
        assertEquals("1 KB", result);
    }

    @Test
    @DisplayName("formatFileSize 应处理大文件")
    void testFormatFileSizeLargeFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, 10737418240L);
        assertTrue(result.contains("GB"));
        assertTrue(result.startsWith("10.0"));
    }

    @Test
    @DisplayName("findExtractedExe 应对 null 目录返回 null")
    void testFindExtractedExeNullDirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        File result = (File) method.invoke(null, (File) null);
        assertNull(result);
    }

    @Test
    @DisplayName("findExtractedExe 应对不存在的目录返回 null")
    void testFindExtractedExeNonExistentDirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        File nonExistent = new File("/non/existent/directory");
        File result = (File) method.invoke(null, nonExistent);
        assertNull(result);
    }

    @Test
    @DisplayName("findExtractedExe 应对空目录返回 null")
    void testFindExtractedExeEmptyDirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        File emptyDir = tempDir.toFile();
        File result = (File) method.invoke(null, emptyDir);
        assertNull(result);
    }

    @Test
    @DisplayName("findExtractedExe 应找到 NeoLink.exe")
    void testFindExtractedExeFound() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        File exeFile = tempDir.resolve("NeoLink.exe").toFile();
        exeFile.createNewFile();

        File result = (File) method.invoke(null, tempDir.toFile());
        assertNotNull(result);
        assertEquals("NeoLink.exe", result.getName());
    }

    @Test
    @DisplayName("findExtractedExe 应在子目录中查找")
    void testFindExtractedExeInSubdirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        File exeFile = subDir.resolve("NeoLink.exe").toFile();
        exeFile.createNewFile();

        File result = (File) method.invoke(null, tempDir.toFile());
        assertNotNull(result);
        assertEquals("NeoLink.exe", result.getName());
    }

    @Test
    @DisplayName("findExtractedExe 应忽略非 exe 文件")
    void testFindExtractedExeIgnoreNonExe() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        File txtFile = tempDir.resolve("NeoLink.txt").toFile();
        txtFile.createNewFile();

        File result = (File) method.invoke(null, tempDir.toFile());
        assertNull(result);
    }

    @Test
    @DisplayName("findExtractedExe 应忽略目录名为 NeoLink.exe")
    void testFindExtractedExeIgnoreDirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        Path dir = tempDir.resolve("NeoLink.exe");
        Files.createDirectories(dir);

        File result = (File) method.invoke(null, tempDir.toFile());
        assertNull(result);
    }

    @Test
    @DisplayName("deleteFileOrDirectory 应对 null 安全处理")
    void testDeleteFileOrDirectoryNull() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null, (File) null));
    }

    @Test
    @DisplayName("deleteFileOrDirectory 应对不存在的文件安全处理")
    void testDeleteFileOrDirectoryNonExistent() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        File nonExistent = new File("/non/existent/file.txt");
        assertDoesNotThrow(() -> method.invoke(null, nonExistent));
    }

    @Test
    @DisplayName("deleteFileOrDirectory 应删除单个文件")
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
    @DisplayName("deleteFileOrDirectory 应删除目录及其内容")
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
    @DisplayName("deleteFileOrDirectory 应处理嵌套目录")
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
    @DisplayName("deleteFileOrDirectory 在 debug 模式下应输出详细信息")
    void testDeleteFileOrDirectoryDebugMode() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);
        NeoLink.isDebugMode = true;

        File file = tempDir.resolve("debug_test.txt").toFile();
        file.createNewFile();

        assertDoesNotThrow(() -> method.invoke(null, file));
        assertFalse(file.exists());
    }

    @Test
    @DisplayName("tempUpdateDir 常量应为当前目录路径")
    void testTempUpdateDirConstant() throws Exception {
        var field = UpdateManager.class.getDeclaredField("tempUpdateDir");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals(NeoLink.CURRENT_DIR_PATH, value);
    }

    @Test
    @DisplayName("downloadFileFromUrl 对无效 URL 应返回 false")
    void testDownloadFileFromUrlInvalidUrl() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("downloadFileFromUrl", String.class, File.class);
        method.setAccessible(true);

        File outputFile = tempDir.resolve("output.txt").toFile();

        Boolean result = (Boolean) method.invoke(null, "not-a-valid-url", outputFile);
        assertFalse(result);
    }

    @Test
    @DisplayName("downloadFileFromUrl 对不存在的域名应返回 false")
    void testDownloadFileFromUrlNonExistentDomain() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("downloadFileFromUrl", String.class, File.class);
        method.setAccessible(true);

        File outputFile = tempDir.resolve("output.txt").toFile();

        Boolean result = (Boolean) method.invoke(null, "http://non-existent-domain-12345.com/file.txt", outputFile);
        assertFalse(result);
    }

    @Test
    @DisplayName("startNewVersion 对不存在的文件应安全处理")
    void testStartNewVersionNonExistentFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("startNewVersion", File.class);
        method.setAccessible(true);

        File nonExistent = new File(tempDir.toFile(), "non-existent.exe");

        assertDoesNotThrow(() -> method.invoke(null, nonExistent));
    }

    @Test
    @DisplayName("startNewVersion 对目录应安全处理")
    void testStartNewVersionDirectory() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("startNewVersion", File.class);
        method.setAccessible(true);

        File directory = tempDir.toFile();

        assertDoesNotThrow(() -> method.invoke(null, directory));
    }

    @Test
    @DisplayName("deleteFileOrDirectory 对只读文件应处理异常")
    void testDeleteFileOrDirectoryReadOnlyFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        File file = tempDir.resolve("readonly.txt").toFile();
        file.createNewFile();
        file.setReadOnly();

        assertDoesNotThrow(() -> method.invoke(null, file));
    }

    @Test
    @DisplayName("deleteFileOrDirectory 对包含只读文件的目录应处理")
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
    @DisplayName("formatFileSize 应处理负数字节")
    void testFormatFileSizeNegative() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, -1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("findExtractedExe 应处理 listFiles 返回 null 的情况")
    void testFindExtractedExeListFilesNull() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.isDirectory()).thenReturn(true);
        when(mockFile.listFiles()).thenReturn(null);

        File result = (File) method.invoke(null, mockFile);
        assertNull(result);
    }

    @Test
    @DisplayName("deleteFileOrDirectory 应处理 listFiles 返回 null")
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
