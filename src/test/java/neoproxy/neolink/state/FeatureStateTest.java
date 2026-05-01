package neoproxy.neolink.state;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FeatureStateTest")
class FeatureStateTest {

    private final FeatureSettings original = FeatureState.snapshot();

    @AfterEach
    void tearDown() {
        FeatureState.apply(original);
    }

    @Test
    @DisplayName("testDefaultSnapshot")
    void testDefaultSnapshot() {
        FeatureState.apply(new FeatureSettings(
                false, true, true, false, false, false, true, true, false, false,
                1000, 30, "", "", null, ""
        ));

        FeatureSettings snapshot = FeatureState.snapshot();
        assertFalse(snapshot.debugMode());
        assertTrue(snapshot.showConnection());
        assertTrue(snapshot.guiMode());
        assertFalse(snapshot.disableTcp());
        assertFalse(snapshot.disableUdp());
        assertFalse(snapshot.enableProxyProtocol());
        assertTrue(snapshot.enableAutoReconnect());
        assertTrue(snapshot.enableAutoUpdate());
        assertFalse(snapshot.testUpdate());
        assertFalse(snapshot.noEffectMode());
        assertEquals(1000, snapshot.heartbeatPacketDelay());
        assertEquals(30, snapshot.reconnectionIntervalSeconds());
        assertEquals("", snapshot.proxyIPToLocalServer());
        assertEquals("", snapshot.proxyIPToNeoServer());
        assertNull(snapshot.outputFilePath());
        assertEquals("", snapshot.nkmNodeListUrl());
    }

    @Test
    @DisplayName("testSettersAndRuntimeTransportSelection")
    void testSettersAndRuntimeTransportSelection() {
        FeatureState.setDebugMode(true);
        FeatureState.setShowConnection(false);
        FeatureState.setGuiMode(false);
        FeatureState.setEnableProxyProtocol(true);
        FeatureState.setEnableAutoReconnect(false);
        FeatureState.setEnableAutoUpdate(false);
        FeatureState.setTestUpdate(true);
        FeatureState.setNoEffectMode(true);
        FeatureState.setHeartbeatPacketDelay(2500);
        FeatureState.setReconnectionIntervalSeconds(45);
        FeatureState.setProxyIPToLocalServer("10.0.0.1");
        FeatureState.setProxyIPToNeoServer("10.0.0.2");
        FeatureState.setOutputFilePath("logs/test.log");
        FeatureState.setNkmNodeListUrl("https://example.com/nodes.json");
        FeatureState.applyRuntimeTransportSelection(false, true);

        FeatureSettings snapshot = FeatureState.snapshot();
        assertTrue(snapshot.debugMode());
        assertFalse(snapshot.showConnection());
        assertFalse(snapshot.guiMode());
        assertTrue(snapshot.disableTcp());
        assertFalse(snapshot.disableUdp());
        assertTrue(snapshot.enableProxyProtocol());
        assertFalse(snapshot.enableAutoReconnect());
        assertFalse(snapshot.enableAutoUpdate());
        assertTrue(snapshot.testUpdate());
        assertTrue(snapshot.noEffectMode());
        assertEquals(2500, snapshot.heartbeatPacketDelay());
        assertEquals(45, snapshot.reconnectionIntervalSeconds());
        assertEquals("10.0.0.1", snapshot.proxyIPToLocalServer());
        assertEquals("10.0.0.2", snapshot.proxyIPToNeoServer());
        assertEquals("logs/test.log", snapshot.outputFilePath());
        assertEquals("https://example.com/nodes.json", snapshot.nkmNodeListUrl());
    }

    @Test
    @DisplayName("testDebugModeSyncsNeoLinkApiDebugger")
    void testDebugModeSyncsNeoLinkApiDebugger() {
        FeatureState.setDebugMode(true);
        assertTrue(top.ceroxe.api.neolink.util.Debugger.isEnabled());

        FeatureState.setDebugMode(false);
        assertFalse(top.ceroxe.api.neolink.util.Debugger.isEnabled());
    }
}
