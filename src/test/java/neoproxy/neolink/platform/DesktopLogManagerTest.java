package neoproxy.neolink.platform;

import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.state.RuntimeState;
import neoproxy.neolink.util.LogSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.ceroxe.api.print.log.LogType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DesktopLogManagerTest")
class DesktopLogManagerTest {
    @TempDir
    Path tempDir;

    private LogSink originalLogSink;
    private String originalWorkingDir;

    @BeforeEach
    void setUp() {
        originalLogSink = RuntimeState.logSink();
        originalWorkingDir = ConfigOperator.WORKING_DIR;
        ConfigOperator.setWorkingDirectoryProvider(() -> tempDir);
        ConfigOperator.WORKING_DIR = tempDir.toString();
    }

    @AfterEach
    void tearDown() {
        DesktopLogManager.closeTunnelLog("tunnel-id");
        RuntimeState.setLogSink(originalLogSink);
        ConfigOperator.setWorkingDirectoryProvider(null);
        ConfigOperator.WORKING_DIR = originalWorkingDir;
    }

    @Test
    @DisplayName("tunnel log keeps Loggist tag format inside logs/tunnels")
    void tunnelLogKeepsLoggistTagFormatInsideLogsTunnels() throws Exception {
        DesktopLogManager.openTunnelLog("tunnel-id", "生产隧道", true);

        DesktopLogManager.logTunnel("tunnel-id", LogType.INFO, "TCP 127.0.0.1:53122 -> 192.168.1.10:8080 已建立");
        DesktopLogManager.closeTunnelLog("tunnel-id");

        Path tunnelLogsDir = tempDir.resolve("logs").resolve("tunnels");
        Path logFile = Files.list(tunnelLogsDir).findFirst().orElseThrow();
        String content = Files.readString(logFile);

        assertTrue(logFile.getFileName().toString().startsWith("生产隧道-"));
        assertTrue(logFile.getFileName().toString().endsWith(".log"));
        assertTrue(content.contains("[INFO] [HOST-CLIENT] TCP 127.0.0.1:53122 -> 192.168.1.10:8080 已建立"));
    }

    @Test
    @DisplayName("tunnel file name validator rejects file-system-invalid names")
    void tunnelFileNameValidatorRejectsFileSystemInvalidNames() {
        assertTrue(DesktopLogManager.isValidTunnelLogFileName("生产隧道"));
        assertEquals(false, DesktopLogManager.isValidTunnelLogFileName("bad:name"));
        assertEquals(false, DesktopLogManager.isValidTunnelLogFileName("bad/name"));
        assertEquals(false, DesktopLogManager.isValidTunnelLogFileName("COM1"));
        assertEquals(false, DesktopLogManager.isValidTunnelLogFileName("隧道."));
    }
}
