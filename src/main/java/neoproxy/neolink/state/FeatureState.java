package neoproxy.neolink.state;

import java.util.function.UnaryOperator;

public final class FeatureState {
    private static FeatureSettings settings = new FeatureSettings(
            false,
            true,
            true,
            false,
            false,
            false,
            true,
            true,
            false,
            false,
            1000,
            30,
            "",
            "",
            null,
            ""
    );

    static {
        top.ceroxe.api.neolink.util.Debugger.setEnabled(settings.debugMode());
    }

    private FeatureState() {
    }

    public static synchronized FeatureSettings snapshot() {
        return settings;
    }

    public static synchronized void apply(FeatureSettings newSettings) {
        if (newSettings == null) {
            return;
        }
        settings = newSettings;
        top.ceroxe.api.neolink.util.Debugger.setEnabled(newSettings.debugMode());
    }

    public static synchronized void update(UnaryOperator<FeatureSettings> updater) {
        if (updater == null) {
            return;
        }
        apply(updater.apply(snapshot()));
    }

    public static void setDebugMode(boolean value) {
        update(current -> copy(current, value, current.showConnection(), current.guiMode(), current.disableTcp(),
                current.disableUdp(), current.enableProxyProtocol(), current.enableAutoReconnect(),
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setShowConnection(boolean value) {
        update(current -> copy(current, current.debugMode(), value, current.guiMode(), current.disableTcp(),
                current.disableUdp(), current.enableProxyProtocol(), current.enableAutoReconnect(),
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setGuiMode(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), value, current.disableTcp(),
                current.disableUdp(), current.enableProxyProtocol(), current.enableAutoReconnect(),
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setDisableTCP(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(), value,
                current.disableUdp(), current.enableProxyProtocol(), current.enableAutoReconnect(),
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setDisableUDP(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), value, current.enableProxyProtocol(), current.enableAutoReconnect(),
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setEnableProxyProtocol(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), value, current.enableAutoReconnect(),
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setEnableAutoReconnect(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(), value,
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setEnableAutoUpdate(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), value, current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setTestUpdate(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), value, current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setNoEffectMode(boolean value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), current.testUpdate(), value,
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setHeartbeatPacketDelay(int value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), current.testUpdate(),
                current.noEffectMode(), value, current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    public static void setReconnectionIntervalSeconds(int value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), current.testUpdate(),
                current.noEffectMode(), current.heartbeatPacketDelay(), value, current.proxyIPToLocalServer(),
                current.proxyIPToNeoServer(), current.outputFilePath(), current.nkmNodeListUrl()));
    }

    public static void setProxyIPToLocalServer(String value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), current.testUpdate(),
                current.noEffectMode(), current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                value, current.proxyIPToNeoServer(), current.outputFilePath(), current.nkmNodeListUrl()));
    }

    public static void setProxyIPToNeoServer(String value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), current.testUpdate(),
                current.noEffectMode(), current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), value, current.outputFilePath(), current.nkmNodeListUrl()));
    }

    public static void setOutputFilePath(String value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), current.testUpdate(),
                current.noEffectMode(), current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), value, current.nkmNodeListUrl()));
    }

    public static void setNkmNodeListUrl(String value) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(),
                current.disableTcp(), current.disableUdp(), current.enableProxyProtocol(),
                current.enableAutoReconnect(), current.enableAutoUpdate(), current.testUpdate(),
                current.noEffectMode(), current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(), value));
    }

    public static void applyRuntimeTransportSelection(boolean tcpEnabled, boolean udpEnabled) {
        update(current -> copy(current, current.debugMode(), current.showConnection(), current.guiMode(), !tcpEnabled,
                !udpEnabled, current.enableProxyProtocol(), current.enableAutoReconnect(),
                current.enableAutoUpdate(), current.testUpdate(), current.noEffectMode(),
                current.heartbeatPacketDelay(), current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(), current.proxyIPToNeoServer(), current.outputFilePath(),
                current.nkmNodeListUrl()));
    }

    private static FeatureSettings copy(
            FeatureSettings ignored,
            boolean debugMode,
            boolean showConnection,
            boolean guiMode,
            boolean disableTcp,
            boolean disableUdp,
            boolean enableProxyProtocol,
            boolean enableAutoReconnect,
            boolean enableAutoUpdate,
            boolean testUpdate,
            boolean noEffectMode,
            int heartbeatPacketDelay,
            int reconnectionIntervalSeconds,
            String proxyIPToLocalServer,
            String proxyIPToNeoServer,
            String outputFilePath,
            String nkmNodeListUrl
    ) {
        return new FeatureSettings(
                debugMode,
                showConnection,
                guiMode,
                disableTcp,
                disableUdp,
                enableProxyProtocol,
                enableAutoReconnect,
                enableAutoUpdate,
                testUpdate,
                noEffectMode,
                heartbeatPacketDelay,
                reconnectionIntervalSeconds,
                proxyIPToLocalServer,
                proxyIPToNeoServer,
                outputFilePath,
                nkmNodeListUrl
        );
    }
}
