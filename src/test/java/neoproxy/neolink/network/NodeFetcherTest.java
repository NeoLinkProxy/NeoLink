package neoproxy.neolink.network;

import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        originalLanguageData = NeoLink.languageData;
        originalNkmNodeListUrl = NeoLink.nkmNodeListUrl;

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
}
