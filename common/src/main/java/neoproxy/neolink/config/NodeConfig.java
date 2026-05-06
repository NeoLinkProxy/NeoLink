package neoproxy.neolink.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * nodes.json 的集中解析器。
 * <p>
 * CLI 和 GUI 过去用两套正则分别解析同一个文件。把结构规则集中在这里，
 * 可以避免两个入口的行为漂移，同时保留现有节点列表结构中的字段名。
 * <p>
 * NeoKeyManager 现在会在显示名称旁提供稳定的 realId。NeoLink 仍把 name
 * 作为面向用户的标签，但用 realId 匹配可以避免显示名称变更时破坏 CLI 自动化。
 */
public final class NodeConfig {
    public static final String NODE_LIST_FILE_NAME = "nodes.json";
    public static final int DEFAULT_HOST_HOOK_PORT = 44801;
    public static final int DEFAULT_HOST_CONNECT_PORT = 44802;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String name;
    private final String realId;
    private final String address;
    private final String icon;
    private final int hostHookPort;
    private final int hostConnectPort;

    private NodeConfig(String name, String realId, String address, String icon, int hostHookPort, int hostConnectPort) {
        this.name = name;
        this.realId = realId;
        this.address = address;
        this.icon = icon;
        this.hostHookPort = hostHookPort;
        this.hostConnectPort = hostConnectPort;
    }

    public static List<NodeConfig> loadAll(File nodeFile) throws IOException {
        return loadAll(nodeFile, false);
    }

    public static List<NodeConfig> loadAll(File nodeFile, boolean allowEmpty) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(nodeFile);
        if (root == null || !root.isArray()) {
            throw new IOException(NODE_LIST_FILE_NAME + " root must be a JSON array.");
        }

        List<NodeConfig> nodes = new ArrayList<>();
        for (JsonNode item : root) {
            NodeConfig parsed = parseNode(item);
            if (parsed == null) {
                throw new IOException(NODE_LIST_FILE_NAME + " contains an invalid node entry.");
            }
            nodes.add(parsed);
        }
        if (nodes.isEmpty() && !allowEmpty) {
            throw new IOException(NODE_LIST_FILE_NAME + " must contain at least one valid node.");
        }
        return nodes;
    }

    public static NodeConfig findByName(File nodeFile, String nodeNameOrRealId) throws IOException {
        if (nodeNameOrRealId == null || nodeNameOrRealId.isBlank()) {
            return null;
        }
        String requestedNode = nodeNameOrRealId.trim();

        JsonNode root = OBJECT_MAPPER.readTree(nodeFile);
        if (root == null || !root.isArray()) {
            throw new IOException(NODE_LIST_FILE_NAME + " root must be a JSON array.");
        }

        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }

            String name = readText(item, "name");
            String realId = readText(item, "realId");
            if (requestedNode.equals(name) || requestedNode.equals(realId)) {
                return parseNode(item);
            }
        }
        return null;
    }

    public static void saveAll(File nodeFile, Collection<top.ceroxe.api.neolink.NeoNode> nodes) throws IOException {
        if (nodeFile == null) {
            throw new IOException(NODE_LIST_FILE_NAME + " target file must not be null.");
        }
        if (nodes == null) {
            throw new IOException(NODE_LIST_FILE_NAME + " source nodes must not be null.");
        }

        File absoluteNodeFile = nodeFile.getAbsoluteFile();
        File parent = absoluteNodeFile.getParentFile();
        if (parent != null) {
            java.nio.file.Files.createDirectories(parent.toPath());
        }

        File tempFile = File.createTempFile("node-list-", ".json", parent);
        try {
            writeAll(tempFile, nodes);
            try {
                java.nio.file.Files.move(tempFile.toPath(), absoluteNodeFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                java.nio.file.Files.move(tempFile.toPath(), absoluteNodeFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile.toPath());
        }
    }

    public static Map<String, top.ceroxe.api.neolink.NeoNode> readNodeMap(File nodeFile, boolean allowEmpty) throws IOException {
        List<NodeConfig> nodeConfigs = loadAll(nodeFile, allowEmpty);
        Map<String, top.ceroxe.api.neolink.NeoNode> nodes = new LinkedHashMap<>();
        for (NodeConfig nodeConfig : nodeConfigs) {
            String key = nodeConfig.realId != null && !nodeConfig.realId.isBlank() ? nodeConfig.realId : nodeConfig.name;
            nodes.put(key, nodeConfig.toNeoNode());
        }
        return nodes;
    }

    private static void writeAll(File nodeFile, Collection<top.ceroxe.api.neolink.NeoNode> nodes) throws IOException {
        List<Map<String, Object>> serializedNodes = new ArrayList<>();
        for (top.ceroxe.api.neolink.NeoNode node : nodes) {
            if (node == null) {
                throw new IOException(NODE_LIST_FILE_NAME + " contains a null node.");
            }
            Map<String, Object> serializedNode = new LinkedHashMap<>();
            serializedNode.put("realId", node.getRealId());
            serializedNode.put("name", node.getName());
            serializedNode.put("address", node.getAddress());
            serializedNode.put("iconSvg", node.getIconSvg());
            serializedNode.put("hookPort", node.getHookPort());
            serializedNode.put("connectPort", node.getConnectPort());
            serializedNodes.add(serializedNode);
        }
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(nodeFile, serializedNodes);
    }

    private static NodeConfig parseNode(JsonNode item) throws IOException {
        if (item == null || !item.isObject()) {
            return null;
        }

        String name = readText(item, "name");
        String address = readText(item, "address");
        if (name == null || name.isBlank() || address == null || address.isBlank()) {
            return null;
        }

        return new NodeConfig(
                name,
                readText(item, "realId"),
                address,
                readText(item, "icon", "iconSvg"),
                readPort(item, DEFAULT_HOST_HOOK_PORT, "HOST_HOOK_PORT", "hookPort"),
                readPort(item, DEFAULT_HOST_CONNECT_PORT, "HOST_CONNECT_PORT", "connectPort")
        );
    }

    private static String readText(JsonNode item, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = item.get(fieldName);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private static int readPort(JsonNode item, int defaultValue, String... aliases) throws IOException {
        for (String alias : aliases) {
            JsonNode value = item.get(alias);
            if (value == null || value.isNull()) {
                continue;
            }

            long port;
            if (value.isIntegralNumber()) {
                port = value.asLong();
            } else if (value.isTextual()) {
                try {
                    port = Long.parseLong(value.asText().trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid port value for " + alias + ": " + value.asText(), e);
                }
            } else {
                throw new IOException("Invalid port value type for " + alias + ".");
            }

            if (port < 1 || port > 65535) {
                throw new IOException("Port out of range for " + alias + ": " + port);
            }
            return (int) port;
        }
        return defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getRealId() {
        return realId;
    }

    public String getAddress() {
        return address;
    }

    public String getIcon() {
        return icon;
    }

    public int getHostHookPort() {
        return hostHookPort;
    }

    public int getHostConnectPort() {
        return hostConnectPort;
    }

    public top.ceroxe.api.neolink.NeoNode toNeoNode() {
        return new top.ceroxe.api.neolink.NeoNode(
                name,
                realId,
                address,
                icon,
                hostHookPort,
                hostConnectPort
        );
    }
}
