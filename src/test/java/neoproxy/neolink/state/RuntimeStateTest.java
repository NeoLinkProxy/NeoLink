package neoproxy.neolink.state;

import neoproxy.neolink.config.LanguageData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.ceroxe.api.print.log.Loggist;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RuntimeStateTest")
class RuntimeStateTest {

    private final String originalTunnelAddress = RuntimeState.tunnelAddress();
    private final Loggist originalLoggist = RuntimeState.loggist();
    private final LanguageData originalLanguageData = RuntimeState.languageData();
    private final boolean originalReconnectedOperation = RuntimeState.isReconnectedOperation();
    private final Scanner originalScanner = RuntimeState.inputScanner();

    @AfterEach
    void tearDown() {
        RuntimeState.setTunnelAddress(originalTunnelAddress);
        RuntimeState.setLoggist(originalLoggist);
        RuntimeState.setLanguageData(originalLanguageData);
        RuntimeState.setReconnectedOperation(originalReconnectedOperation);
        RuntimeState.setInputScanner(originalScanner);
    }

    @Test
    @DisplayName("testDefaults")
    void testDefaults() {
        RuntimeState.setTunnelAddress(null);
        RuntimeState.setLoggist(null);
        RuntimeState.setLanguageData(null);
        RuntimeState.setReconnectedOperation(false);

        assertNull(RuntimeState.tunnelAddress());
        assertNull(RuntimeState.loggist());
        assertNull(RuntimeState.languageData());
        assertFalse(RuntimeState.isReconnectedOperation());
        assertNotNull(RuntimeState.inputScanner());
    }

    @Test
    @DisplayName("testSetters")
    void testSetters() {
        LanguageData languageData = LanguageData.getChineseLanguage();
        Scanner scanner = new Scanner(
                new ByteArrayInputStream("demo".getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8
        );
        Loggist loggist = new Loggist(new File("build/tmp/runtime-state-test.log"));

        RuntimeState.setTunnelAddress("127.0.0.1:14455");
        RuntimeState.setLanguageData(languageData);
        RuntimeState.setInputScanner(scanner);
        RuntimeState.setLoggist(loggist);
        RuntimeState.setReconnectedOperation(true);

        assertTrue(RuntimeState.tunnelAddress().contains("14455"));
        assertSame(languageData, RuntimeState.languageData());
        assertSame(scanner, RuntimeState.inputScanner());
        assertSame(loggist, RuntimeState.loggist());
        assertTrue(RuntimeState.isReconnectedOperation());
    }
}
