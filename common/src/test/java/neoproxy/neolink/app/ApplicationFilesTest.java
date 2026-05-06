package neoproxy.neolink.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApplicationFilesTest")
class ApplicationFilesTest {

    @Test
    @DisplayName("当前可执行体路径解析不抛异常 / current executable resolution does not throw")
    void currentExecutableResolutionDoesNotThrow() {
        File currentExecutable = ApplicationFiles.currentExecutableFile();

        // 在 common 模块中（Android 或 IDE 运行），可能返回 null，但不应抛异常
        if (currentExecutable != null) {
            assertTrue(currentExecutable.exists());
        }
    }
}
