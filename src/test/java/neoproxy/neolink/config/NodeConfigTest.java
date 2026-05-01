package neoproxy.neolink.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.ceroxe.api.neolink.NeoNode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeConfig node cache")
class NodeConfigTest {
    @TempDir
    File tempDir;

    @Test
    @DisplayName("saveAll persists API NeoNode with readable nodes.json schema")
    void saveAllPersistsApiNeoNodes() throws Exception {
        File nodesJson = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        NeoNode apiNode = new NeoNode(
                "China - Suqian Official",
                "node-suqian",
                "p.ceroxe.top",
                "<svg viewBox='0 0 1 1'></svg>",
                44801,
                44802
        );

        NodeConfig.saveAll(nodesJson, List.of(apiNode));

        assertTrue(nodesJson.isFile());
        String savedJson = Files.readString(nodesJson.toPath(), StandardCharsets.UTF_8);
        assertTrue(savedJson.contains("\"realId\""));
        assertTrue(savedJson.contains("\"iconSvg\""));

        List<NodeConfig> loadedNodes = NodeConfig.loadAll(nodesJson);
        assertEquals(1, loadedNodes.size());
        NodeConfig loadedNode = loadedNodes.get(0);
        assertEquals("China - Suqian Official", loadedNode.getName());
        assertEquals("node-suqian", loadedNode.getRealId());
        assertEquals("p.ceroxe.top", loadedNode.getAddress());
        assertEquals("<svg viewBox='0 0 1 1'></svg>", loadedNode.getIcon());
        assertEquals(44801, loadedNode.getHostHookPort());
        assertEquals(44802, loadedNode.getHostConnectPort());
    }

    @Test
    @DisplayName("saveAll keeps existing cache when serialization input is invalid")
    void saveAllKeepsExistingCacheWhenInputIsInvalid() throws Exception {
        File nodesJson = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        String existingJson = "[{\"realId\":\"node-cached\",\"name\":\"cached\",\"address\":\"cached.example.com\"}]";
        Files.writeString(nodesJson.toPath(), existingJson, StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> NodeConfig.saveAll(nodesJson, Collections.singletonList((NeoNode) null)));

        assertEquals(existingJson, Files.readString(nodesJson.toPath(), StandardCharsets.UTF_8));
    }
}
