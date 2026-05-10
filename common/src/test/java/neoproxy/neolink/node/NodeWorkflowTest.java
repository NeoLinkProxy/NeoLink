package neoproxy.neolink.node;

import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.app.ApplicationFiles;
import neoproxy.neolink.state.ConnectionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.ceroxe.api.neolink.NeoLinkCfg;
import top.ceroxe.api.neolink.NeoNode;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("NodeWorkflowTest")
class NodeWorkflowTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        ConnectionState.setSpecifiedNodeName(null);
        ConnectionState.setRemoteDomainName("localhost");
        ConnectionState.setHostHookPort(NodeConfig.DEFAULT_HOST_HOOK_PORT);
        ConnectionState.setHostConnectPort(NodeConfig.DEFAULT_HOST_CONNECT_PORT);
    }

    @Test
    @DisplayName("无指定节点时返回空 / returns null when node is not specified")
    void returnsNullWhenNodeIsNotSpecified() {
        ConnectionState.setSpecifiedNodeName(null);

        assertNull(NodeWorkflow.loadSelectedNodeConfiguration());
    }

    @Test
    @DisplayName("可从 nodes.json 加载指定节点 / loads selected node from nodes file")
    void loadsSelectedNodeFromNodesFile() throws Exception {
        ConfigOperator.WORKING_DIR = tempDir.toString();
        File nodeFile = ApplicationFiles.nodesCacheFile();
        NodeConfig.saveAll(nodeFile, List.of(new NeoNode("demo", "demo", "example.com", "", 4100, 4200)));
        ConnectionState.setSpecifiedNodeName("demo");

        NeoNode node = NodeWorkflow.loadSelectedNodeConfiguration();

        assertNotNull(node);
        assertEquals("example.com", ConnectionState.snapshot().remoteDomainName());
        assertEquals(4100, ConnectionState.snapshot().hostHookPort());
        assertEquals(4200, ConnectionState.snapshot().hostConnectPort());
    }

    @Test
    @DisplayName("可基于静态状态构建配置 / builds tunnel config from state")
    void buildsTunnelConfigFromState() {
        ConnectionState.setRemoteDomainName("example.com");
        ConnectionState.setHostHookPort(4100);
        ConnectionState.setHostConnectPort(4200);
        ConnectionState.setKey("access-key");
        ConnectionState.setLocalPort(8080);

        NeoLinkCfg cfg = NodeWorkflow.buildTunnelConfig(null);

        assertEquals("example.com", cfg.getRemoteDomainName());
        assertEquals(4100, cfg.getHookPort());
        assertEquals(4200, cfg.getHostConnectPort());
        assertEquals("access-key", cfg.getKey());
        assertEquals(8080, cfg.getLocalPort());
    }
}
