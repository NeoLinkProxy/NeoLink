package neoproxy.neolink.state;

import neoproxy.neolink.config.NodeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ConnectionStateTest")
class ConnectionStateTest {
    private static final int INVALID_LOCAL_PORT = -1;

    private final ConnectionSettings original = ConnectionState.snapshot();

    @AfterEach
    void tearDown() {
        ConnectionState.apply(original);
    }

    @Test
    @DisplayName("testDefaultSnapshot")
    void testDefaultSnapshot() {
        ConnectionState.apply(new ConnectionSettings(
                "localhost",
                "localhost",
                NodeConfig.DEFAULT_HOST_HOOK_PORT,
                NodeConfig.DEFAULT_HOST_CONNECT_PORT,
                null,
                INVALID_LOCAL_PORT,
                null
        ));

        ConnectionSettings snapshot = ConnectionState.snapshot();
        assertEquals("localhost", snapshot.remoteDomainName());
        assertEquals("localhost", snapshot.localDomainName());
        assertEquals(NodeConfig.DEFAULT_HOST_HOOK_PORT, snapshot.hostHookPort());
        assertEquals(NodeConfig.DEFAULT_HOST_CONNECT_PORT, snapshot.hostConnectPort());
        assertNull(snapshot.key());
        assertEquals(INVALID_LOCAL_PORT, snapshot.localPort());
        assertNull(snapshot.specifiedNodeName());
    }

    @Test
    @DisplayName("testSetters")
    void testSetters() {
        ConnectionState.setRemoteDomainName("remote.example.com");
        ConnectionState.setLocalDomainName("127.0.0.1");
        ConnectionState.setHostHookPort(44901);
        ConnectionState.setHostConnectPort(44902);
        ConnectionState.setKey("access-key");
        ConnectionState.setLocalPort(25565);
        ConnectionState.setSpecifiedNodeName("node-id");

        ConnectionSettings snapshot = ConnectionState.snapshot();
        assertEquals("remote.example.com", snapshot.remoteDomainName());
        assertEquals("127.0.0.1", snapshot.localDomainName());
        assertEquals(44901, snapshot.hostHookPort());
        assertEquals(44902, snapshot.hostConnectPort());
        assertEquals("access-key", snapshot.key());
        assertEquals(25565, snapshot.localPort());
        assertEquals("node-id", snapshot.specifiedNodeName());
    }
}
