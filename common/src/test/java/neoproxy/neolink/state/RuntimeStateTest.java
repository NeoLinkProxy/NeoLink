package neoproxy.neolink.state;

import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.util.LogSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RuntimeStateTest")
class RuntimeStateTest {

    private final String originalTunnelAddress = RuntimeState.tunnelAddress();
    private final LogSink originalLogSink = RuntimeState.logSink();
    private final LanguageData originalLanguageData = RuntimeState.languageData();
    private final boolean originalReconnectedOperation = RuntimeState.isReconnectedOperation();

    @AfterEach
    void tearDown() {
        RuntimeState.setTunnelAddress(originalTunnelAddress);
        RuntimeState.setLogSink(originalLogSink);
        RuntimeState.setLanguageData(originalLanguageData);
        RuntimeState.setReconnectedOperation(originalReconnectedOperation);
    }

    @Test
    @DisplayName("testDefaults")
    void testDefaults() {
        RuntimeState.setTunnelAddress(null);
        RuntimeState.setLogSink(null);
        RuntimeState.setLanguageData(null);
        RuntimeState.setReconnectedOperation(false);

        assertNull(RuntimeState.tunnelAddress());
        assertNull(RuntimeState.logSink());
        assertNull(RuntimeState.languageData());
        assertFalse(RuntimeState.isReconnectedOperation());
    }

    @Test
    @DisplayName("testSetters")
    void testSetters() {
        LanguageData languageData = LanguageData.getChineseLanguage();
        LogSink logSink = (level, tag, message) -> { };

        RuntimeState.setTunnelAddress("127.0.0.1:44802");
        RuntimeState.setLanguageData(languageData);
        RuntimeState.setLogSink(logSink);
        RuntimeState.setReconnectedOperation(true);

        assertTrue(RuntimeState.tunnelAddress().contains("44802"));
        assertSame(languageData, RuntimeState.languageData());
        assertSame(logSink, RuntimeState.logSink());
        assertTrue(RuntimeState.isReconnectedOperation());
    }
}
