package neoproxy.neolink.update;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.config.LanguageData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

/**
 * `UpdateManagerMockTest` 回归测试。
 */
@DisplayName("UpdateManagerMockTest")
class UpdateManagerMockTest {

    @BeforeEach
    void setUp() {
        RuntimeState.setLanguageData(new LanguageData());
        FeatureState.setDebugMode(false);
        FeatureState.setGuiMode(true);
    }

    @AfterEach
    void tearDown() {
        RuntimeState.setLanguageData(null);
    }

    @Test
    @DisplayName("testFormatFileSize")
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
    @DisplayName("testDeleteFileOrDirectoryNull")
    void testDeleteFileOrDirectoryNull() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("deleteFileOrDirectory", File.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null, (File) null));
    }

    @Test
    @DisplayName("testStartInstallerNonExistentFile")
    void testStartInstallerNonExistentFile() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("startInstaller", File.class);
        method.setAccessible(true);

        File nonExistent = new File("non_existent_file.exe");

        Boolean result = (Boolean) method.invoke(null, nonExistent);

        assertFalse(result);
    }

    @Test
    @DisplayName("testStartInstallerUsesInstallerOnly")
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
