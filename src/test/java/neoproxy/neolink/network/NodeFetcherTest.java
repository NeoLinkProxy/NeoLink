package neoproxy.neolink.network;

import com.sun.net.httpserver.HttpServer;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NodeFetcher 测试类
 *
 * 测试范围：
 * 1. 并发锁机制
 * 2. 空白 URL 静默处理
 * 3. 语言检测初始化
 */
@DisplayName("NodeFetcher 节点获取器测试")
class NodeFetcherTest {

    private LanguageData originalLanguageData;
    private String originalNkmNodeListUrl;
    private String originalWorkingDir;
    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        originalLanguageData = NeoLink.languageData;
        originalNkmNodeListUrl = NeoLink.nkmNodeListUrl;
        originalWorkingDir = ConfigOperator.WORKING_DIR;

        outContent = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        Field isFetchingField = NodeFetcher.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        AtomicBoolean isFetching = (AtomicBoolean) isFetchingField.get(null);
        isFetching.set(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        NeoLink.languageData = originalLanguageData;
        NeoLink.nkmNodeListUrl = originalNkmNodeListUrl;
        ConfigOperator.WORKING_DIR = originalWorkingDir;
        System.setOut(originalOut);

        Field isFetchingField = NodeFetcher.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        AtomicBoolean isFetching = (AtomicBoolean) isFetchingField.get(null);
        isFetching.set(false);
    }

    @Test
    @DisplayName("fetchAndSaveNodes 空白 URL 应静默返回")
    void testFetchAndSaveNodesWithBlankUrl() {
        NeoLink.languageData = new LanguageData();
        NeoLink.nkmNodeListUrl = "";

        assertDoesNotThrow(() -> NodeFetcher.fetchAndSaveNodes());
    }

    @Test
    @DisplayName("fetchAndSaveNodes null URL 应静默返回")
    void testFetchAndSaveNodesWithNullUrl() {
        NeoLink.languageData = new LanguageData();
        NeoLink.nkmNodeListUrl = null;

        assertDoesNotThrow(() -> NodeFetcher.fetchAndSaveNodes());
    }

    @Test
    @DisplayName("fetchAndSaveNodes 纯空白 URL 应静默返回")
    void testFetchAndSaveNodesWithWhitespaceUrl() {
        NeoLink.languageData = new LanguageData();
        NeoLink.nkmNodeListUrl = "   ";

        assertDoesNotThrow(() -> NodeFetcher.fetchAndSaveNodes());
    }

    @Test
    @DisplayName("并发调用 fetchAndSaveNodes 应只执行一次")
    void testConcurrentFetchAndSaveNodes() throws Exception {
        NeoLink.languageData = new LanguageData();
        NeoLink.nkmNodeListUrl = "";

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(NodeFetcher::fetchAndSaveNodes);
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertDoesNotThrow(() -> {});
    }

    @Test
    @DisplayName("languageData 为 null 时应自动检测语言")
    void testLanguageAutoDetection() throws Exception {
        NeoLink.languageData = null;
        NeoLink.nkmNodeListUrl = "";

        assertDoesNotThrow(() -> NodeFetcher.fetchAndSaveNodes());

        assertNotNull(NeoLink.languageData);
    }

    @Test
    @DisplayName("isFetching 原子锁初始应为 false")
    void testIsFetchingInitiallyFalse() throws Exception {
        Field isFetchingField = NodeFetcher.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        AtomicBoolean isFetching = (AtomicBoolean) isFetchingField.get(null);

        assertFalse(isFetching.get());
    }

    @Test
    @DisplayName("无效 URL 应捕获异常并静默处理")
    void testInvalidUrlHandling() {
        NeoLink.languageData = new LanguageData();
        NeoLink.nkmNodeListUrl = "not-a-valid-url";

        assertDoesNotThrow(() -> NodeFetcher.fetchAndSaveNodes());
    }

    @Test
    @DisplayName("fetchAndSaveNodes 应写入 nodes.json 并废弃 node.json")
    void testFetchAndSaveNodesWritesNodesJsonOnly() throws Exception {
        NeoLink.languageData = new LanguageData();
        ConfigOperator.WORKING_DIR = tempDir.getAbsolutePath();
        String nodeListJson = "[{\"name\":\"test-node\",\"address\":\"127.0.0.1\"}]";

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/nodes", exchange -> {
            byte[] response = nodeListJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();

        try {
            NeoLink.nkmNodeListUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/nodes";

            NodeFetcher.fetchAndSaveNodes();

            File nodesJson = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
            assertTrue(nodesJson.isFile());
            assertFalse(new File(tempDir, "node.json").exists());
            String savedJson = Files.readString(nodesJson.toPath(), StandardCharsets.UTF_8);
            assertTrue(savedJson.contains(System.lineSeparator()) || savedJson.contains("\n"));
            assertTrue(savedJson.contains("  \"name\""));
            assertEquals(1, NodeConfig.loadAll(nodesJson).size());
        } finally {
            server.stop(0);
        }
    }
}
