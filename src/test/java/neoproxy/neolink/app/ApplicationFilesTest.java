package neoproxy.neolink.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApplicationFilesTest")
class ApplicationFilesTest {

    @Test
    @DisplayName("当前可执行体路径可解析 / current executable can be resolved")
    void currentExecutableCanBeResolved() {
        File currentExecutable = ApplicationFiles.currentExecutableFile();

        assertNotNull(currentExecutable);
        assertTrue(currentExecutable.exists());
    }
}
