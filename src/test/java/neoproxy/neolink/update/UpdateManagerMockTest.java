package neoproxy.neolink.update;

import fun.ceroxe.api.OshiUtils;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UpdateManager 静态方法 Mock 测试
 * 
 * 使用 mockito-inline 来 mock 静态方法
 */
@DisplayName("UpdateManager 静态方法 Mock 测试")
class UpdateManagerMockTest {

    @BeforeEach
    void setUp() {
        NeoLink.languageData = new LanguageData();
        NeoLink.isDebugMode = false;
        NeoLink.isGUIMode = true;
    }

    @AfterEach
    void tearDown() {
        NeoLink.languageData = null;
    }

    @Test
    @DisplayName("formatFileSize 应正确格式化各种大小")
    void testFormatFileSize() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);

        assertEquals("0 B", method.invoke(null, 0L));
        assertEquals("512 B", method.invoke(null, 512L));
        assertEquals("1 KB", method.invoke(null, 1024L));
        assertEquals("1.00 MB", method.invoke(null, 1024L * 1024L));
        assertEquals("1.00 GB", method.invoke(null, 1024L * 1024L * 1024L));
    }

    @Test
    @DisplayName("deleteFileOrDirectory 应处理 null 输入")
    void testDeleteFileOrDirectoryNull() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null, (File) null));
    }

    @Test
    @DisplayName("findExtractedExe 应处理 null 目录")
    void testFindExtractedExeNull() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("findExtractedExe", File.class);
        method.setAccessible(true);

        File result = (File) method.invoke(null, (File) null);
        assertNull(result);
    }

    @Test
    @DisplayName("startNewVersion 应安全处理不存在的文件")
    void testStartNewVersionNonExistentFile() throws Exception {
        try (MockedStatic<OshiUtils> oshiMock = mockStatic(OshiUtils.class)) {
            
            oshiMock.when(OshiUtils::isWindows).thenReturn(true);
            
            Method method = UpdateManager.class.getDeclaredMethod("startNewVersion", File.class);
            method.setAccessible(true);

            File nonExistent = new File("non_existent_file.exe");
            
            assertDoesNotThrow(() -> method.invoke(null, nonExistent));
        }
    }

    @Test
    @DisplayName("startNewVersion 在 Windows 上应使用 ProcessBuilder")
    void testStartNewVersionWindows() throws Exception {
        try (MockedStatic<OshiUtils> oshiMock = mockStatic(OshiUtils.class);
             MockedConstruction<ProcessBuilder> processBuilderMock = mockConstruction(ProcessBuilder.class,
                     (builder, context) -> when(builder.start()).thenReturn(mock(Process.class)))) {
            
            oshiMock.when(OshiUtils::isWindows).thenReturn(true);
            
            Method method = UpdateManager.class.getDeclaredMethod("startNewVersion", File.class);
            method.setAccessible(true);

            File tempFile = File.createTempFile("test", ".exe");
            tempFile.deleteOnExit();
            
            assertDoesNotThrow(() -> method.invoke(null, tempFile));
            assertEquals(1, processBuilderMock.constructed().size());
            verify(processBuilderMock.constructed().get(0)).start();
        }
    }

    @Test
    @DisplayName("startNewVersion 在非 Windows 上应使用 ProcessBuilder")
    void testStartNewVersionNonWindows() throws Exception {
        try (MockedStatic<OshiUtils> oshiMock = mockStatic(OshiUtils.class);
             MockedConstruction<ProcessBuilder> processBuilderMock = mockConstruction(ProcessBuilder.class,
                     (builder, context) -> {
                         when(builder.inheritIO()).thenReturn(builder);
                         when(builder.start()).thenReturn(mock(Process.class));
                     })) {
            
            oshiMock.when(OshiUtils::isWindows).thenReturn(false);
            
            Method method = UpdateManager.class.getDeclaredMethod("startNewVersion", File.class);
            method.setAccessible(true);

            File tempFile = File.createTempFile("test", ".exe");
            tempFile.deleteOnExit();
            
            assertDoesNotThrow(() -> method.invoke(null, tempFile));
            assertEquals(1, processBuilderMock.constructed().size());
            verify(processBuilderMock.constructed().get(0)).inheritIO();
            verify(processBuilderMock.constructed().get(0)).start();
        }
    }
}
