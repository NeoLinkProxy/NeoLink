package neoproxy.neolink.update;

import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
    @DisplayName("startInstaller 应安全处理不存在的文件")
    void testStartInstallerNonExistentFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("startInstaller", File.class);
        method.setAccessible(true);

        File nonExistent = new File("non_existent_file.exe");

        Boolean result = (Boolean) method.invoke(null, nonExistent);

        assertFalse(result);
    }

    @Test
    @DisplayName("startInstaller 应只启动 installer 本身")
    void testStartInstallerUsesInstallerOnly() throws Exception {
        AtomicReference<List<?>> constructorArguments = new AtomicReference<>();

        try (MockedConstruction<ProcessBuilder> processBuilderMock = mockConstruction(ProcessBuilder.class,
                (builder, context) -> {
                    constructorArguments.set(context.arguments());
                    when(builder.start()).thenReturn(mock(Process.class));
                })) {

            Method method = UpdateManager.class.getDeclaredMethod("startInstaller", File.class);
            method.setAccessible(true);

            File tempFile = File.createTempFile("test", ".exe");
            tempFile.deleteOnExit();

            Boolean result = (Boolean) method.invoke(null, tempFile);

            assertTrue(result);
            assertEquals(1, processBuilderMock.constructed().size());
            assertEquals(List.of(tempFile.getAbsolutePath()), constructorArguments.get().get(0));
            verify(processBuilderMock.constructed().get(0)).start();
        }
    }
}
