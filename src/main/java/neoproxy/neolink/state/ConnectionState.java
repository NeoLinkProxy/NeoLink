package neoproxy.neolink.state;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.config.NodeConfig;

import java.util.function.UnaryOperator;

public final class ConnectionState {
    private static ConnectionSettings settings = new ConnectionSettings(
            "localhost",
            "localhost",
            NodeConfig.DEFAULT_HOST_HOOK_PORT,
            NodeConfig.DEFAULT_HOST_CONNECT_PORT,
            null,
            NeoLink.INVALID_LOCAL_PORT,
            null
    );

    private ConnectionState() {
    }

    public static synchronized ConnectionSettings snapshot() {
        return settings;
    }

    public static synchronized void apply(ConnectionSettings newSettings) {
        if (newSettings == null) {
            return;
        }
        settings = newSettings;
    }

    public static synchronized void update(UnaryOperator<ConnectionSettings> updater) {
        if (updater == null) {
            return;
        }
        apply(updater.apply(snapshot()));
    }

    public static void setRemoteDomainName(String value) {
        update(current -> new ConnectionSettings(
                value,
                current.localDomainName(),
                current.hostHookPort(),
                current.hostConnectPort(),
                current.key(),
                current.localPort(),
                current.specifiedNodeName()
        ));
    }

    public static void setLocalDomainName(String value) {
        update(current -> new ConnectionSettings(
                current.remoteDomainName(),
                value,
                current.hostHookPort(),
                current.hostConnectPort(),
                current.key(),
                current.localPort(),
                current.specifiedNodeName()
        ));
    }

    public static void setHostHookPort(int value) {
        update(current -> new ConnectionSettings(
                current.remoteDomainName(),
                current.localDomainName(),
                value,
                current.hostConnectPort(),
                current.key(),
                current.localPort(),
                current.specifiedNodeName()
        ));
    }

    public static void setHostConnectPort(int value) {
        update(current -> new ConnectionSettings(
                current.remoteDomainName(),
                current.localDomainName(),
                current.hostHookPort(),
                value,
                current.key(),
                current.localPort(),
                current.specifiedNodeName()
        ));
    }

    public static void setKey(String value) {
        update(current -> new ConnectionSettings(
                current.remoteDomainName(),
                current.localDomainName(),
                current.hostHookPort(),
                current.hostConnectPort(),
                value,
                current.localPort(),
                current.specifiedNodeName()
        ));
    }

    public static void setLocalPort(int value) {
        update(current -> new ConnectionSettings(
                current.remoteDomainName(),
                current.localDomainName(),
                current.hostHookPort(),
                current.hostConnectPort(),
                current.key(),
                value,
                current.specifiedNodeName()
        ));
    }

    public static void setSpecifiedNodeName(String value) {
        update(current -> new ConnectionSettings(
                current.remoteDomainName(),
                current.localDomainName(),
                current.hostHookPort(),
                current.hostConnectPort(),
                current.key(),
                current.localPort(),
                value
        ));
    }
}
