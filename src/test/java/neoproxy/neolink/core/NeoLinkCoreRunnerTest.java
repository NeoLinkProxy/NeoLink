package neoproxy.neolink.core;

import neoproxy.neolink.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.ceroxe.api.neolink.NeoLinkAPI;
import top.ceroxe.api.neolink.NeoLinkCfg;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import neoproxy.neolink.state.FeatureSettings;
import neoproxy.neolink.state.FeatureState;

@DisplayName("NeoLinkCoreRunnerTest")
class NeoLinkCoreRunnerTest {

    @AfterEach
    void tearDown() throws Exception {
        Field tunnelField = NeoLinkCoreRunner.class.getDeclaredField("tunnel");
        tunnelField.setAccessible(true);
        tunnelField.set(null, null);

        Field transportSelectionField = NeoLinkCoreRunner.class.getDeclaredField("transportSelection");
        transportSelectionField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> selection = (AtomicReference<Object>) transportSelectionField.get(null);
        Class<?> selectionType = Class.forName("neoproxy.neolink.core.NeoLinkCoreRunner$TransportSelection");
        var constructor = selectionType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object defaultSelection = constructor.newInstance(true, true);
        selection.set(defaultSelection);

        FeatureState.applyRuntimeTransportSelection(true, true);
    }

    @Test
    @DisplayName("testUpdateRuntimeProtocolFlagsWithActiveTunnel")
    void testUpdateRuntimeProtocolFlagsWithActiveTunnel() throws Exception {
        NeoLinkAPI api = mock(NeoLinkAPI.class);
        when(api.isActive()).thenReturn(true);

        Field tunnelField = NeoLinkCoreRunner.class.getDeclaredField("tunnel");
        tunnelField.setAccessible(true);
        tunnelField.set(null, api);

        NeoLinkCoreRunner.updateRuntimeProtocolFlags(false, true);

        assertTrue(FeatureState.snapshot().disableTcp());
        assertFalse(FeatureState.snapshot().disableUdp());
        verify(api).updateRuntimeProtocolFlags(false, true);
    }

    @Test
    @DisplayName("testUpdateRuntimeProtocolFlagsWithoutActiveTunnel")
    void testUpdateRuntimeProtocolFlagsWithoutActiveTunnel() {
        assertDoesNotThrow(() -> NeoLinkCoreRunner.updateRuntimeProtocolFlags(true, false));
        assertFalse(FeatureState.snapshot().disableTcp());
        assertTrue(FeatureState.snapshot().disableUdp());
    }

    @Test
    @DisplayName("testBuildTunnelUsesLatestTransportSelection")
    void testBuildTunnelUsesLatestTransportSelection() throws Exception {
        FeatureState.apply(new FeatureSettings(
                false, true, true, false, false, false, true, true, false, false,
                1000, 30, "", "", null, ""
        ));
        NeoLinkCoreRunner.updateRuntimeProtocolFlags(false, true);
        NeoLinkCoreRunner.updateRuntimeProtocolFlags(true, false);

        AtomicReference<NeoLinkCfg> capturedCfg = new AtomicReference<>();
        try (var construction = mockConstruction(NeoLinkAPI.class, (mock, context) -> {
            NeoLinkCfg cfg = (NeoLinkCfg) context.arguments().get(0);
            capturedCfg.set(cfg);
            when(mock.setUnsupportedVersionDecision(any())).thenReturn(mock);
            when(mock.setOnStateChanged(any())).thenReturn(mock);
            when(mock.setOnServerMessage(any())).thenReturn(mock);
            when(mock.setOnError(any())).thenReturn(mock);
            when(mock.setOnConnect(any())).thenReturn(mock);
            when(mock.setOnDisconnect(any())).thenReturn(mock);
            when(mock.setOnConnectNeoFailure(any())).thenReturn(mock);
            when(mock.setOnConnectLocalFailure(any())).thenReturn(mock);
            when(mock.setDebugSink(any())).thenReturn(mock);
        })) {
            Method buildTunnel = NeoLinkCoreRunner.class.getDeclaredMethod(
                    "buildTunnel",
                    NeoLinkCfg.class,
                    AtomicBoolean.class
            );
            buildTunnel.setAccessible(true);
            Object tunnel = buildTunnel.invoke(
                    null,
                    new NeoLinkCfg("example.com", 44801, 44802, "token", 8080),
                    new AtomicBoolean(false)
            );

            assertNotNull(tunnel);
            assertNotNull(capturedCfg.get());
            assertTrue(capturedCfg.get().isTCPEnabled());
            assertFalse(capturedCfg.get().isUDPEnabled());
        }
    }

    @Test
    @DisplayName("testProtocolSwitchPersistsForNextTunnelBuildWithoutActiveTunnel")
    void testProtocolSwitchPersistsForNextTunnelBuildWithoutActiveTunnel() throws Exception {
        NeoLinkCoreRunner.updateRuntimeProtocolFlags(false, false);

        AtomicReference<NeoLinkCfg> capturedCfg = new AtomicReference<>();
        try (var construction = mockConstruction(NeoLinkAPI.class, (mock, context) -> {
            NeoLinkCfg cfg = (NeoLinkCfg) context.arguments().get(0);
            capturedCfg.set(cfg);
            when(mock.setUnsupportedVersionDecision(any())).thenReturn(mock);
            when(mock.setOnStateChanged(any())).thenReturn(mock);
            when(mock.setOnServerMessage(any())).thenReturn(mock);
            when(mock.setOnError(any())).thenReturn(mock);
            when(mock.setOnConnect(any())).thenReturn(mock);
            when(mock.setOnDisconnect(any())).thenReturn(mock);
            when(mock.setOnConnectNeoFailure(any())).thenReturn(mock);
            when(mock.setOnConnectLocalFailure(any())).thenReturn(mock);
            when(mock.setDebugSink(any())).thenReturn(mock);
        })) {
            Method buildTunnel = NeoLinkCoreRunner.class.getDeclaredMethod(
                    "buildTunnel",
                    NeoLinkCfg.class,
                    AtomicBoolean.class
            );
            buildTunnel.setAccessible(true);
            buildTunnel.invoke(
                    null,
                    new NeoLinkCfg("example.com", 44801, 44802, "token", 8080),
                    new AtomicBoolean(false)
            );

            assertNotNull(capturedCfg.get());
            assertFalse(capturedCfg.get().isTCPEnabled());
            assertFalse(capturedCfg.get().isUDPEnabled());
        }
    }
}
