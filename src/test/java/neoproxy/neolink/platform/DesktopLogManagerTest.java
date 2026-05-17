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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        DesktopLogManager.shutdown();
        RuntimeState.setLogSink(originalLogSink);
        ConfigOperator.setWorkingDirectoryProvider(null);
        ConfigOperator.WORKING_DIR = originalWorkingDir;
    }

    @Test
    @DisplayName("global UI log writes one Loggist file and mirrors Loggist-formatted text")
    void globalUiLogWritesFileAndMirrorsFormattedText() throws Exception {
        List<String> mirrored = new ArrayList<>();

        DesktopLogManager.initialize(true);
        DesktopLogManager.attachMirror((level, tag, message) -> mirrored.add(message));
        RuntimeState.logSink().log(LogSink.Level.INFO, "UI", "桌面日志");
        DesktopLogManager.shutdown();

        Path uiLogsDir = tempDir.resolve("logs").resolve("ui");
        Path logFile = Files.list(uiLogsDir).findFirst().orElseThrow();
        String content = Files.readString(logFile);

        assertTrue(logFile.getFileName().toString().startsWith("UI-"));
        assertTrue(logFile.getFileName().toString().endsWith(".log"));
        assertTrue(content.contains("[INFO] [UI] 桌面日志"));
        assertEquals(1, mirrored.size());
        assertTrue(mirrored.get(0).contains("[INFO] [UI] 桌面日志"));
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
    @DisplayName("logs redact credentials while preserving key diagnostics")
    void logsRedactCredentialsWhilePreservingKeyDiagnostics() throws Exception {
        List<String> mirrored = new ArrayList<>();

        DesktopLogManager.initialize(true);
        DesktopLogManager.attachMirror((level, tag, message) -> mirrored.add(message));
        RuntimeState.logSink().log(LogSink.Level.INFO, "UI", "写入密钥 abcdef123456 password=super-secret token:session-token");
        DesktopLogManager.shutdown();

        Path uiLogsDir = tempDir.resolve("logs").resolve("ui");
        String content = readAllLogFiles(uiLogsDir);

        assertTrue(content.contains("写入密钥 abc***"));
        assertTrue(content.contains("password=***"));
        assertTrue(content.contains("token:***"));
        assertTrue(content.contains("[INFO] [UI]"));
        assertTrue(mirrored.get(0).contains("写入密钥 abc***"));
        assertEquals(false, content.contains("abcdef123456"));
        assertEquals(false, content.contains("super-secret"));
        assertEquals(false, content.contains("session-token"));
    }

    @Test
    @DisplayName("tunnel logs redact written keys on disk")
    void tunnelLogsRedactWrittenKeysOnDisk() throws Exception {
        DesktopLogManager.openTunnelLog("tunnel-id", "生产隧道", true);

        DesktopLogManager.logTunnel("tunnel-id", LogType.INFO, "已写入密钥 neoproxy-secret-key 到配置。");
        DesktopLogManager.closeTunnelLog("tunnel-id");

        Path tunnelLogsDir = tempDir.resolve("logs").resolve("tunnels");
        Path logFile = Files.list(tunnelLogsDir).findFirst().orElseThrow();
        String content = Files.readString(logFile);

        assertTrue(content.contains("已写入密钥 neo*** 到配置。"));
        assertEquals(false, content.contains("neoproxy-secret-key"));
    }

    @Test
    @DisplayName("key balance diagnostics are not redacted as secrets")
    void keyBalanceDiagnosticsAreNotRedactedAsSecrets() {
        String sanitized = DesktopLogManager.sanitizeForLog("这个密钥有 80.5 MB 流量可以消耗。");

        assertEquals("这个密钥有 80.5 MB 流量可以消耗。", sanitized);
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

    private String readAllLogFiles(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }
}
