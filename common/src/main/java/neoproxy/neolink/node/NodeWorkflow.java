package neoproxy.neolink.node;

import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import neoproxy.neolink.util.LogSink;
import neoproxy.neolink.util.MessageSink;
import top.ceroxe.api.neolink.NeoLinkCfg;
import top.ceroxe.api.neolink.NeoNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Map;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * 节点工作流。
 *
 * <p>集中处理公共节点列表拉取、nodes.json 落盘、指定节点加载与
 * 隧道配置构建，避免入口类混杂 I/O、解析和业务分支。</p>
 *
 * <p>跨平台适配：消息输出通过 {@link MessageSink} 接口注入，不再直接依赖 CLI 的 ClientConsole。</p>
 */
public final class NodeWorkflow {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * 消息输出目标。必须在使用 fetchAndSaveNodes 前通过 {@link #setMessageSink} 注入。
     * 默认为空操作，避免 NPE。
     */
    private static volatile MessageSink messageSink = (msg, level) -> { };

    private NodeWorkflow() {
    }

    /**
     * 注入消息输出实现。Desktop CLI 注入 ClientConsole 适配器，GUI 注入 ViewModel 适配器。
     */
    public static void setMessageSink(MessageSink sink) {
        if (sink != null) {
            messageSink = sink;
        }
    }

    public static void fetchAndSaveNodes() {
        ensureLanguageDetected();
        String nodeListUrl = FeatureState.snapshot().nkmNodeListUrl();
        LanguageData currentLanguage = RuntimeState.languageData();
        if (nodeListUrl == null || nodeListUrl.isBlank()) {
            return;
        }

        messageSink.say(currentLanguage.FETCHING_NODE_LIST + nodeListUrl, LogSink.Level.INFO);
        try {
            Map<String, NeoNode> nodes = fetchNodesFromNkm(nodeListUrl);
            if (nodes.isEmpty()) {
                messageSink.say(currentLanguage.NODE_LIST_EMPTY, LogSink.Level.INFO);
                return;
            }
            saveFetchedNodes(nodes);
            messageSink.say(currentLanguage.NODE_LIST_FETCH_SUCCESS, LogSink.Level.INFO);
        } catch (IOException | IllegalArgumentException e) {
            debugOperation(e);
            messageSink.say(currentLanguage.NODE_LIST_FETCH_FAIL, LogSink.Level.WARNING);
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

    private static void saveFetchedNodes(Map<String, NeoNode> nodes) throws IOException {
        File workingDir = new File(ConfigOperator.WORKING_DIR);
        Files.createDirectories(workingDir.toPath());
        File nodeFile = new File(workingDir, NodeConfig.NODE_LIST_FILE_NAME);
        NodeConfig.saveAll(nodeFile, nodes.values());
    }

    private static Map<String, NeoNode> fetchNodesFromNkm(String nodeListUrl) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(nodeListUrl))
                .GET()
                .build();
        File tempFile = Files.createTempFile("nkm-node-list-", ".json").toFile();
        try {
            HttpResponse<InputStream> response;
            try {
                response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching node list.", e);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Node list request failed with HTTP " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                Files.copy(body, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return NodeConfig.readNodeMap(tempFile, true);
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    private static void ensureLanguageDetected() {
        if (RuntimeState.languageData() == null) {
            LanguageManager.detectLanguage();
        }
    }
}
