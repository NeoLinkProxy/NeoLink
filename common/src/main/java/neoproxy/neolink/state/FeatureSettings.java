package neoproxy.neolink.state;

public record FeatureSettings(
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
}
