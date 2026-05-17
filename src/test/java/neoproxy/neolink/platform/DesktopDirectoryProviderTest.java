package neoproxy.neolink.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DesktopDirectoryProviderTest")
class DesktopDirectoryProviderTest {

    private final String originalOsName = System.getProperty("os.name");
    private final String originalUserHome = System.getProperty("user.home");

    @AfterEach
    void restoreSystemProperties() {
        restoreProperty("os.name", originalOsName);
        restoreProperty("user.home", originalUserHome);
    }

    @Test
    @DisplayName("resolveWorkingDirectory uses hidden NeoLink folder on Linux")
    void resolveWorkingDirectoryUsesHiddenNeoLinkFolderOnLinux(@TempDir Path home) {
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", home.toString());

        Path workingDirectory = new DesktopDirectoryProvider().resolveWorkingDirectory();

        assertEquals(home.resolve(".neolink").toAbsolutePath(), workingDirectory);
        assertTrue(Files.isDirectory(workingDirectory));
    }

    @Test
    @DisplayName("resolveWorkingDirectory uses application support folder on macOS")
    void resolveWorkingDirectoryUsesApplicationSupportFolderOnMacOs(@TempDir Path home) {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("user.home", home.toString());

        Path workingDirectory = new DesktopDirectoryProvider().resolveWorkingDirectory();

        Path expected = home.resolve("Library").resolve("Application Support").resolve("NeoLink").toAbsolutePath();
        assertEquals(expected, workingDirectory);
        assertTrue(Files.isDirectory(workingDirectory));
    }

    @Test
    @DisplayName("resolveWorkingDirectory fails fast when home path is not a directory")
    void resolveWorkingDirectoryFailsFastWhenHomePathIsNotDirectory(@TempDir Path tempDir) throws Exception {
        Path homeFile = tempDir.resolve("not-a-directory");
        Files.writeString(homeFile, "occupied");
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", homeFile.toString());

        assertThrows(IllegalStateException.class, () -> new DesktopDirectoryProvider().resolveWorkingDirectory());
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, value);
    }
}
