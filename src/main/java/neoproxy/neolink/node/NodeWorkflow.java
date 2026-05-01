package neoproxy.neolink.node;

import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.cli.ClientConsole;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import top.ceroxe.api.neolink.NeoLinkCfg;
import top.ceroxe.api.neolink.NeoNode;
import top.ceroxe.api.print.log.LogType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * 节点工作流（node workflow）。
 *
 * <p>集中处理 public node list 拉取、nodes.json 落盘、指定节点加载（selected node loading）与
 * tunnel config 构建，避免入口类混杂 I/O、解析和业务分支。</p>
 */
public final class NodeWorkflow {

    private NodeWorkflow() {
    }

    public static void fetchAndSaveNodes() {
        ensureLanguageDetected();
        String nodeListUrl = FeatureState.snapshot().nkmNodeListUrl();
        LanguageData currentLanguage = RuntimeState.languageData();
        if (nodeListUrl == null || nodeListUrl.isBlank()) {
            return;
        }

        ClientConsole.say(currentLanguage.FETCHING_NODE_LIST + nodeListUrl, LogType.INFO);
        try {
            Map<String, top.ceroxe.api.neolink.NeoNode> nodes =
                    top.ceroxe.api.neolink.NodeFetcher.getFromNKM(nodeListUrl);
            if (nodes.isEmpty()) {
                ClientConsole.say(currentLanguage.NODE_LIST_EMPTY, LogType.INFO);
                return;
            }
            saveFetchedNodes(nodes);
            ClientConsole.say(currentLanguage.NODE_LIST_FETCH_SUCCESS, LogType.INFO);
        } catch (IOException | IllegalArgumentException e) {
            debugOperation(e);
            ClientConsole.say(currentLanguage.NODE_LIST_FETCH_FAIL, LogType.WARNING);
        }
    }

    public static NeoNode loadSelectedNodeConfiguration() {
        String nodeName = ConnectionState.snapshot().specifiedNodeName();
        if (nodeName == null || nodeName.isBlank()) {
            return null;
        }
        debugOperation("Attempting to load configuration for node: " + nodeName);
        File nodeFile = new File(ConfigOperator.WORKING_DIR, NodeConfig.NODE_LIST_FILE_NAME);
        try {
            if (!nodeFile.exists()) {
                throw new IllegalArgumentException(NodeConfig.NODE_LIST_FILE_NAME + " file not found.");
            }
            NodeConfig node = NodeConfig.findByName(nodeFile, nodeName);
            if (node == null) {
                throw new IllegalArgumentException("Node not found.");
            }
            ConnectionState.setRemoteDomainName(node.getAddress());
            ConnectionState.setHostHookPort(node.getHostHookPort());
            ConnectionState.setHostConnectPort(node.getHostConnectPort());
            return node.toNeoNode();
        } catch (Exception e) {
            debugOperation("Failed to load node config: " + e.getMessage());
            return null;
        }
    }

    public static NeoLinkCfg buildTunnelConfig(NeoNode selectedNode) {
        if (selectedNode != null) {
            return selectedNode.toCfg(ConnectionState.snapshot().key(), ConnectionState.snapshot().localPort());
        }
        return new NeoLinkCfg(
                ConnectionState.snapshot().remoteDomainName(),
                ConnectionState.snapshot().hostHookPort(),
                ConnectionState.snapshot().hostConnectPort(),
                ConnectionState.snapshot().key(),
                ConnectionState.snapshot().localPort()
        );
    }

    private static void saveFetchedNodes(Map<String, top.ceroxe.api.neolink.NeoNode> nodes) throws IOException {
        File workingDir = new File(ConfigOperator.WORKING_DIR);
        Files.createDirectories(workingDir.toPath());
        File nodeFile = new File(workingDir, NodeConfig.NODE_LIST_FILE_NAME);
        NodeConfig.saveAll(nodeFile, nodes.values());
    }

    private static void ensureLanguageDetected() {
        if (RuntimeState.languageData() == null) {
            LanguageManager.detectLanguage();
        }
    }
}
