package neoproxy.neolink.cli;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CommandLineProcessorTest")
class CommandLineProcessorTest {

    @AfterEach
    void tearDown() {
        ConnectionState.setKey(null);
        ConnectionState.setLocalPort(NeoLink.INVALID_LOCAL_PORT);
        ConnectionState.setSpecifiedNodeName(null);
        FeatureState.setOutputFilePath(null);
        FeatureState.setGuiMode(false);
        FeatureState.setDebugMode(false);
        FeatureState.setDisableTCP(false);
        FeatureState.setDisableUDP(false);
        FeatureState.setEnableProxyProtocol(false);
        FeatureState.setTestUpdate(false);
        FeatureState.setNoEffectMode(false);
        RuntimeState.setLanguageData(null);
    }

    @Test
    @DisplayName("CLI 参数写入状态 / cli args update state")
    void cliArgsUpdateState() {
        FeatureState.setGuiMode(true);

        LaunchOptions launchOptions = CommandLineProcessor.applyCommandLineArgs(new String[]{
                "--key=test-key",
                "--local-port=8080",
                "--node=demo-node",
                "--output-file=logs/test.log",
                "--debug",
                "--disable-udp",
                "--no-color"
        });

        assertEquals("test-key", ConnectionState.snapshot().key());
        assertEquals(8080, ConnectionState.snapshot().localPort());
        assertEquals("demo-node", ConnectionState.snapshot().specifiedNodeName());
        assertEquals("logs/test.log", FeatureState.snapshot().outputFilePath());
        assertTrue(FeatureState.snapshot().debugMode());
        assertTrue(FeatureState.snapshot().disableUdp());
        assertTrue(launchOptions.autoStartInGui());
        assertTrue(launchOptions.noColor());
    }

    @Test
    @DisplayName("端口非法时拒绝 / invalid port is rejected")
    void invalidPortIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CommandLineProcessor.applyCommandLineArgs(new String[]{"--local-port=70000"})
        );

        assertTrue(exception.getMessage().contains("between 1 and 65535"));
    }

    @Test
    @DisplayName("缺省参数不自动启动 / missing key pieces disable auto-start")
    void missingKeyPiecesDisableAutoStart() {
        FeatureState.setGuiMode(true);

        LaunchOptions launchOptions = CommandLineProcessor.applyCommandLineArgs(new String[]{"--key=test-key"});

        assertFalse(launchOptions.autoStartInGui());
    }

    @Test
    @DisplayName("GUI 模式强制中文，忽略 CLI 语言参数")
    void guiModeForcesChineseLanguage() {
        FeatureState.setGuiMode(true);

        CommandLineProcessor.applyCommandLineArgs(new String[]{"--en-us"});

        assertNotNull(RuntimeState.languageData());
        assertEquals("zh", RuntimeState.languageData().getCurrentLanguage());
    }
}
