package neoproxy.neolink.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

/**
 * `DebuggerTest` 回归测试。
 */
@DisplayName("DebuggerTest")
class DebuggerTest {
    private boolean originalDebugMode;
    private boolean originalGuiMode;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private LogSink originalLogSink;

    @BeforeEach
    void setUp() {
        originalDebugMode = FeatureState.snapshot().debugMode();
        originalGuiMode = FeatureState.snapshot().guiMode();
        originalLogSink = RuntimeState.logSink();

        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        FeatureState.setDebugMode(originalDebugMode);
        FeatureState.setGuiMode(originalGuiMode);
        RuntimeState.setLogSink(originalLogSink);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("testDebugOperationStringWhenDebugModeOff")
    void testDebugOperationStringWhenDebugModeOff() {
        FeatureState.setDebugMode(false);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(false);

        Debugger.debugOperation("Test message");

        assertTrue(outContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationStringWhenDebugModeOnAndCliMode")
    void testDebugOperationStringWhenDebugModeOnAndCliMode() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(false);

        Debugger.debugOperation("Test debug message");

        String output = outContent.toString();
        assertTrue(output.contains("[DEBUG]"));
        assertTrue(output.contains("Test debug message"));
    }

    @Test
    @DisplayName("testDebugOperationStringWhenDebugModeOnAndGuiMode")
    void testDebugOperationStringWhenDebugModeOnAndGuiMode() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(true);

        Debugger.debugOperation("Test debug message");

        assertTrue(outContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationStringWhenDebugModeOnAndGuiModeWithLogSink")
    void testDebugOperationStringWhenDebugModeOnAndGuiModeWithLogSink() {
        FeatureState.setDebugMode(true);
        FeatureState.setGuiMode(true);
        CapturingLogSink capturingLogSink = new CapturingLogSink();
        RuntimeState.setLogSink(capturingLogSink);

        Debugger.debugOperation("Test debug message");

        assertTrue(outContent.toString().isEmpty());
        assertNotNull(capturingLogSink.lastMessage);
        assertEquals(LogSink.Level.INFO, capturingLogSink.lastLevel);
        assertEquals("UI", capturingLogSink.lastTag);
        assertEquals("Test debug message", capturingLogSink.lastMessage);
    }

    @Test
    @DisplayName("testDebugOperationExceptionWritesToLogSink")
    void testDebugOperationExceptionWritesToLogSink() {
        FeatureState.setDebugMode(true);
        FeatureState.setGuiMode(true);
        CapturingLogSink capturingLogSink = new CapturingLogSink();
        RuntimeState.setLogSink(capturingLogSink);

        Debugger.debugOperation(new RuntimeException("Persisted debug message"));

        assertEquals(LogSink.Level.ERROR, capturingLogSink.lastLevel);
        assertEquals("UI", capturingLogSink.lastTag);
        assertTrue(capturingLogSink.lastMessage.contains("Persisted debug message"));
    }

    @Test
    @DisplayName("testDebugOperationExceptionWhenDebugModeOff")
    void testDebugOperationExceptionWhenDebugModeOff() {
        FeatureState.setDebugMode(false);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(false);

        Exception testException = new RuntimeException("Test exception");
        Debugger.debugOperation(testException);

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationExceptionWhenDebugModeOnAndCliMode")
    void testDebugOperationExceptionWhenDebugModeOnAndCliMode() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(false);

        Exception testException = new RuntimeException("Test exception message");
        Debugger.debugOperation(testException);

        String output = errContent.toString();
        assertTrue(output.contains("[DEBUG-EXCEPTION]"));
        assertTrue(output.contains("RuntimeException"));
        assertTrue(output.contains("Test exception message"));
    }

    @Test
    @DisplayName("testDebugOperationExceptionWhenDebugModeOnAndGuiMode")
    void testDebugOperationExceptionWhenDebugModeOnAndGuiMode() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(true);

        Exception testException = new RuntimeException("Test exception");
        Debugger.debugOperation(testException);

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationExceptionWithNull")
    void testDebugOperationExceptionWithNull() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(false);

        assertDoesNotThrow(() -> Debugger.debugOperation((Exception) null));

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationStringWithNull")
    void testDebugOperationStringWithNull() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(false);

        assertDoesNotThrow(() -> Debugger.debugOperation((String) null));
    }

    @Test
    @DisplayName("testExceptionStackTraceContainsFullInfo")
    void testExceptionStackTraceContainsFullInfo() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLogSink(null);
        FeatureState.setGuiMode(false);

        Exception nestedException = new RuntimeException("Outer", new IllegalArgumentException("Inner"));
        Debugger.debugOperation(nestedException);

        String output = errContent.toString();
        assertTrue(output.contains("RuntimeException"));
        assertTrue(output.contains("Outer"));
        assertTrue(output.contains("IllegalArgumentException"));
        assertTrue(output.contains("Inner"));
        assertTrue(output.contains("Caused by"));
    }

    private static final class CapturingLogSink implements LogSink {
        private Level lastLevel;
        private String lastTag;
        private String lastMessage;

        @Override
        public void log(Level level, String tag, String message) {
            lastLevel = level;
            lastTag = tag;
            lastMessage = message;
        }
    }
}
