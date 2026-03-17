package neoproxy.neolink.util;

import fun.ceroxe.api.print.log.LogType;
import fun.ceroxe.api.print.log.State;
import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Debugger 测试类
 *
 * 测试范围：
 * 1. 调试模式开启时的输出行为
 * 2. 调试模式关闭时的静默行为
 * 3. 异常堆栈输出
 * 4. GUI/CLI 模式下的不同输出行为
 */
@DisplayName("Debugger 调试器测试")
class DebuggerTest {

    private boolean originalDebugMode;
    private boolean originalGuiMode;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        originalDebugMode = NeoLink.isDebugMode;
        originalGuiMode = NeoLink.isGUIMode;

        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        NeoLink.isDebugMode = originalDebugMode;
        NeoLink.isGUIMode = originalGuiMode;
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("调试模式关闭时 debugOperation(String) 不应输出")
    void testDebugOperationStringWhenDebugModeOff() {
        NeoLink.isDebugMode = false;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = false;

        Debugger.debugOperation("Test message");

        assertTrue(outContent.toString().isEmpty());
    }

    @Test
    @DisplayName("调试模式开启且 CLI 模式时 debugOperation(String) 应输出到控制台")
    void testDebugOperationStringWhenDebugModeOnAndCliMode() {
        NeoLink.isDebugMode = true;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = false;

        Debugger.debugOperation("Test debug message");

        String output = outContent.toString();
        assertTrue(output.contains("[DEBUG]"));
        assertTrue(output.contains("Test debug message"));
    }

    @Test
    @DisplayName("调试模式开启且 GUI 模式时 debugOperation(String) 不应输出到控制台")
    void testDebugOperationStringWhenDebugModeOnAndGuiMode() {
        NeoLink.isDebugMode = true;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = true;

        Debugger.debugOperation("Test debug message");

        assertTrue(outContent.toString().isEmpty());
    }

    @Test
    @DisplayName("调试模式关闭时 debugOperation(Exception) 不应输出")
    void testDebugOperationExceptionWhenDebugModeOff() {
        NeoLink.isDebugMode = false;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = false;

        Exception testException = new RuntimeException("Test exception");
        Debugger.debugOperation(testException);

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("调试模式开启且 CLI 模式时 debugOperation(Exception) 应输出堆栈")
    void testDebugOperationExceptionWhenDebugModeOnAndCliMode() {
        NeoLink.isDebugMode = true;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = false;

        Exception testException = new RuntimeException("Test exception message");
        Debugger.debugOperation(testException);

        String output = errContent.toString();
        assertTrue(output.contains("[DEBUG-EXCEPTION]"));
        assertTrue(output.contains("RuntimeException"));
        assertTrue(output.contains("Test exception message"));
    }

    @Test
    @DisplayName("调试模式开启且 GUI 模式时 debugOperation(Exception) 不应输出到控制台")
    void testDebugOperationExceptionWhenDebugModeOnAndGuiMode() {
        NeoLink.isDebugMode = true;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = true;

        Exception testException = new RuntimeException("Test exception");
        Debugger.debugOperation(testException);

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("传入 null 异常时不应崩溃")
    void testDebugOperationExceptionWithNull() {
        NeoLink.isDebugMode = true;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = false;

        assertDoesNotThrow(() -> Debugger.debugOperation((Exception) null));

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("传入 null 字符串时不应崩溃")
    void testDebugOperationStringWithNull() {
        NeoLink.isDebugMode = true;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = false;

        assertDoesNotThrow(() -> Debugger.debugOperation((String) null));
    }

    @Test
    @DisplayName("异常堆栈应包含完整信息")
    void testExceptionStackTraceContainsFullInfo() {
        NeoLink.isDebugMode = true;
        NeoLink.loggist = null;
        NeoLink.isGUIMode = false;

        Exception nestedException = new RuntimeException("Outer", new IllegalArgumentException("Inner"));
        Debugger.debugOperation(nestedException);

        String output = errContent.toString();
        assertTrue(output.contains("RuntimeException"));
        assertTrue(output.contains("Outer"));
        assertTrue(output.contains("IllegalArgumentException"));
        assertTrue(output.contains("Inner"));
        assertTrue(output.contains("Caused by"));
    }
}
