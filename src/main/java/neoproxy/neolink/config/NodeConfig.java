package neoproxy.neolink.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized parser for node.json.
 *
 * The CLI and GUI used to parse the same file with separate regular-expression
 * implementations. Keeping the schema rules here prevents drift between the two
 * entry points while preserving the existing node.json field names.
 */
public final class NodeConfig {
    public static final int DEFAULT_HOST_HOOK_PORT = 44801;
    public static final int DEFAULT_HOST_CONNECT_PORT = 44802;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String name;
    private final String address;
    private final String icon;
    private final int hostHookPort;
    private final int hostConnectPort;

    private NodeConfig(String name, String address, String icon, int hostHookPort, int hostConnectPort) {
        this.name = name;
        this.address = address;
        this.icon = icon;
        this.hostHookPort = hostHookPort;
        this.hostConnectPort = hostConnectPort;
    }

    public static List<NodeConfig> loadAll(File nodeFile) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(nodeFile);
        if (root == null || !root.isArray()) {
            throw new IOException("node.json root must be a JSON array.");
        }

        List<NodeConfig> nodes = new ArrayList<>();
        for (JsonNode item : root) {
            NodeConfig parsed = parseNode(item);
            if (parsed != null) {
                nodes.add(parsed);
            }
        }
        return nodes;
    }

    public static NodeConfig findByName(File nodeFile, String nodeName) throws IOException {
        if (nodeName == null || nodeName.isBlank()) {
            return null;
        }

        JsonNode root = OBJECT_MAPPER.readTree(nodeFile);
        if (root == null || !root.isArray()) {
            throw new IOException("node.json root must be a JSON array.");
        }

        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }

            String name = readText(item, "name");
            if (nodeName.equals(name)) {
                return parseNode(item);
            }
        }
        return null;
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
                address,
                readText(item, "icon"),
                readPort(item, DEFAULT_HOST_HOOK_PORT, "HOST_HOOK_PORT", "hookPort"),
                readPort(item, DEFAULT_HOST_CONNECT_PORT, "HOST_CONNECT_PORT", "connectPort")
        );
    }

    private static String readText(JsonNode item, String fieldName) {
        JsonNode value = item.get(fieldName);
        return value != null && value.isTextual() ? value.asText() : null;
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
}
