package neoproxy.neolink.util;

import neoproxy.neolink.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.ceroxe.api.print.log.LogType;
import top.ceroxe.api.print.log.Loggist;
import top.ceroxe.api.print.log.State;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

/**
 * DebuggerTest regression tests.
 */
@DisplayName("DebuggerTest")
class DebuggerTest {
    @TempDir
    Path tempDir;

    private boolean originalDebugMode;
    private boolean originalGuiMode;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private Loggist originalLoggist;

    @BeforeEach
    void setUp() {
        originalDebugMode = FeatureState.snapshot().debugMode();
        originalGuiMode = FeatureState.snapshot().guiMode();
        originalLoggist = RuntimeState.loggist();

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
        RuntimeState.setLoggist(originalLoggist);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("testDebugOperationStringWhenDebugModeOff")
    void testDebugOperationStringWhenDebugModeOff() {
        FeatureState.setDebugMode(false);
        RuntimeState.setLoggist(null);
        FeatureState.setGuiMode(false);

        Debugger.debugOperation("Test message");

        assertTrue(outContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationStringWhenDebugModeOnAndCliMode")
    void testDebugOperationStringWhenDebugModeOnAndCliMode() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLoggist(null);
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
        RuntimeState.setLoggist(null);
        FeatureState.setGuiMode(true);

        Debugger.debugOperation("Test debug message");

        assertTrue(outContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationStringWhenDebugModeOnAndGuiModeWithLoggist")
    void testDebugOperationStringWhenDebugModeOnAndGuiModeWithLoggist() {
        FeatureState.setDebugMode(true);
        FeatureState.setGuiMode(true);
        CapturingLoggist capturingLoggist = new CapturingLoggist(new File("build/tmp/debugger-test-gui.log"));
        try {
            RuntimeState.setLoggist(capturingLoggist);

            Debugger.debugOperation("Test debug message");

            assertTrue(outContent.toString().isEmpty());
            assertNotNull(capturingLoggist.lastState);
            assertEquals(LogType.INFO, capturingLoggist.lastState.type());
            assertEquals("DEBUG", capturingLoggist.lastState.subject());
            assertEquals("Test debug message", capturingLoggist.lastState.content());
        } finally {
            capturingLoggist.close();
        }
    }

    @Test
    @DisplayName("testDebugOperationStringWritesToRealLogFile")
    void testDebugOperationStringWritesToRealLogFile() throws Exception {
        FeatureState.setDebugMode(true);
        FeatureState.setGuiMode(true);
        Path logFile = tempDir.resolve("debugger-real-write.log");
        Loggist realLoggist = new Loggist(logFile.toFile());
        try {
            RuntimeState.setLoggist(realLoggist);

            Debugger.debugOperation("Persisted debug message");

            String content = waitForFileContent(logFile, "Persisted debug message");
            assertTrue(content.contains("[DEBUG]"));
            assertTrue(content.contains("Persisted debug message"));
        } finally {
            realLoggist.close();
        }
    }

    @Test
    @DisplayName("testDebugOperationExceptionWhenDebugModeOff")
    void testDebugOperationExceptionWhenDebugModeOff() {
        FeatureState.setDebugMode(false);
        RuntimeState.setLoggist(null);
        FeatureState.setGuiMode(false);

        Exception testException = new RuntimeException("Test exception");
        Debugger.debugOperation(testException);

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationExceptionWhenDebugModeOnAndCliMode")
    void testDebugOperationExceptionWhenDebugModeOnAndCliMode() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLoggist(null);
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
        RuntimeState.setLoggist(null);
        FeatureState.setGuiMode(true);

        Exception testException = new RuntimeException("Test exception");
        Debugger.debugOperation(testException);

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationExceptionWithNull")
    void testDebugOperationExceptionWithNull() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLoggist(null);
        FeatureState.setGuiMode(false);

        assertDoesNotThrow(() -> Debugger.debugOperation((Exception) null));

        assertTrue(errContent.toString().isEmpty());
    }

    @Test
    @DisplayName("testDebugOperationStringWithNull")
    void testDebugOperationStringWithNull() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLoggist(null);
        FeatureState.setGuiMode(false);

        assertDoesNotThrow(() -> Debugger.debugOperation((String) null));
    }

    @Test
    @DisplayName("testExceptionStackTraceContainsFullInfo")
    void testExceptionStackTraceContainsFullInfo() {
        FeatureState.setDebugMode(true);
        RuntimeState.setLoggist(null);
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

    private static final class CapturingLoggist extends Loggist {
        private State lastState;

        private CapturingLoggist(File logFile) {
            super(logFile);
        }

        @Override
        public void say(State state) {
            lastState = state;
        }
    }

    /**
     * Loggist uses asynchronous queued persistence, so tests must wait for the durable file content instead of
     * assuming the write is visible immediately after debugOperation(...).
     */
    private static String waitForFileContent(Path logFile, String expectedText) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        String content = "";
        while (System.nanoTime() < deadline) {
            if (Files.exists(logFile)) {
                content = Files.readString(logFile, StandardCharsets.UTF_8);
                if (content.contains(expectedText)) {
                    return content;
                }
            }
            Thread.sleep(25L);
        }
        return content;
    }
}
