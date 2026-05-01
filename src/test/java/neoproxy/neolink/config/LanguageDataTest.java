package neoproxy.neolink.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LanguageData 测试类
 * <p>
 * 测试范围：
 * 1. 默认英文语言实例创建
 * 2. 中文语言实例创建
 * 3. flush 方法语言切换
 * 4. sayReconnectMsg 中英文输出
 * 5. getCurrentLanguage 语言标识
 */
@DisplayName("LanguageDataTest")
class LanguageDataTest {

    private LanguageData englishData;
    private LanguageData chineseData;

    @BeforeEach
    void setUp() {
        englishData = new LanguageData();
        chineseData = LanguageData.getChineseLanguage();
    }

    @Test
    @DisplayName("testDefaultConstructorCreatesEnglishInstance")
    void testDefaultConstructorCreatesEnglishInstance() {
        LanguageData data = new LanguageData();

        assertEquals("en", data.getCurrentLanguage());
        assertEquals("The server is offline.", data.SERVER_IS_OFFLINE);
        assertEquals("This should be an integer.", data.IT_MUST_BE_INT);
        assertEquals("The input port range should be between 1~65535.", data.PORT_OUT_OF_RANGE_MSG);
    }

    @Test
    @DisplayName("testGetChineseLanguageCreatesChineseInstance")
    void testGetChineseLanguageCreatesChineseInstance() {
        LanguageData data = LanguageData.getChineseLanguage();

        assertEquals("zh", data.getCurrentLanguage());
        assertEquals("服务端离线。", data.SERVER_IS_OFFLINE);
        assertEquals("这应该为整数。", data.IT_MUST_BE_INT);
        assertEquals("输入的端口范围应在1~65535之间。", data.PORT_OUT_OF_RANGE_MSG);
    }

    @Test
    @DisplayName("testChineseInstanceTranslatesAllFields")
    void testChineseInstanceTranslatesAllFields() {
        assertEquals("服务端离线。", chineseData.SERVER_IS_OFFLINE);
        assertEquals("请输入密钥：", chineseData.PLEASE_ENTER_ACCESS_CODE);
        assertEquals("连接 ", chineseData.CONNECT_TO);
        assertEquals(" ...", chineseData.OMITTED);
        assertEquals("一个 TCP 连接 ", chineseData.A_TCP_CONNECTION);
        assertEquals("一个 UDP 连接 ", chineseData.A_UDP_CONNECTION);
        assertEquals(" 的通道建立", chineseData.BUILD_UP);
        assertEquals("请输入你想进行内网穿透的内网端口：", chineseData.ENTER_PORT_MSG);
        assertEquals("使用链接地址： ", chineseData.USE_THE_ADDRESS);
        assertEquals(" 来从公网连接。", chineseData.TO_START_UP_CONNECTION);
        assertEquals("服务器连接成功", chineseData.CONNECTION_BUILD_UP_SUCCESSFULLY);
        assertEquals("连接以下地址失败：", chineseData.FAIL_TO_BUILD_A_CHANNEL_FROM);
        assertEquals(" 的通道关闭", chineseData.DESTROY);
        assertEquals("连接本地地址失败：localhost:", chineseData.FAIL_TO_CONNECT_LOCALHOST);
        assertEquals("开始下载更新。", chineseData.START_TO_DOWNLOAD_UPDATE);
        assertEquals("下载更新成功。", chineseData.DOWNLOAD_SUCCESS);
    }

    @Test
    @DisplayName("testFlushReturnsEnglishInstanceWhenCurrentIsEnglish")
    void testFlushReturnsEnglishInstanceWhenCurrentIsEnglish() {
        LanguageData data = new LanguageData();
        LanguageData flushed = data.flush();

        assertEquals("en", flushed.getCurrentLanguage());
        assertNotSame(data, flushed);
    }

    @Test
    @DisplayName("testFlushReturnsChineseInstanceWhenCurrentIsChinese")
    void testFlushReturnsChineseInstanceWhenCurrentIsChinese() {
        LanguageData data = LanguageData.getChineseLanguage();
        LanguageData flushed = data.flush();

        assertEquals("zh", flushed.getCurrentLanguage());
        assertNotSame(data, flushed);
    }

    @Test
    @DisplayName("testSayReconnectMsgEnglishOutput")
    void testSayReconnectMsgEnglishOutput() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            LanguageData data = new LanguageData();
            data.sayReconnectMsg(30);

            String output = outContent.toString().trim();
            assertTrue(output.contains("Reconnection will begin after 30 seconds."));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("testSayReconnectMsgChineseOutput")
    void testSayReconnectMsgChineseOutput() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            LanguageData data = LanguageData.getChineseLanguage();
            data.sayReconnectMsg(15);

            String output = outContent.toString().trim();
            assertTrue(output.contains("15 秒后将开始重新连接。"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("testGetCurrentLanguage")
    void testGetCurrentLanguage() {
        assertEquals("en", englishData.getCurrentLanguage());
        assertEquals("zh", chineseData.getCurrentLanguage());
    }

    @Test
    @DisplayName("testEnglishInstanceContainsAllDefaultMessages")
    void testEnglishInstanceContainsAllDefaultMessages() {
        assertNotNull(englishData.PLEASE_UPDATE_MANUALLY);
        assertNotNull(englishData.A_UDP_CONNECTION);
        assertNotNull(englishData.SERVER_IS_OFFLINE);
        assertNotNull(englishData.IT_MUST_BE_INT);
        assertNotNull(englishData.PORT_OUT_OF_RANGE_MSG);
        assertNotNull(englishData.START_TO_DOWNLOAD_UPDATE);
        assertNotNull(englishData.DOWNLOAD_SUCCESS);
        assertNotNull(englishData.PLEASE_RUN);
        assertNotNull(englishData.IF_YOU_SEE_EULA);
        assertNotNull(englishData.VERSION);
        assertNotNull(englishData.PLEASE_ENTER_ACCESS_CODE);
        assertNotNull(englishData.CONNECT_TO);
        assertNotNull(englishData.OMITTED);
        assertNotNull(englishData.A_TCP_CONNECTION);
        assertNotNull(englishData.BUILD_UP);
        assertNotNull(englishData.ENTER_PORT_MSG);
        assertNotNull(englishData.USE_THE_ADDRESS);
        assertNotNull(englishData.TO_START_UP_CONNECTION);
        assertNotNull(englishData.CONNECTION_BUILD_UP_SUCCESSFULLY);
        assertNotNull(englishData.FAIL_TO_BUILD_A_CHANNEL_FROM);
        assertNotNull(englishData.DESTROY);
        assertNotNull(englishData.FAIL_TO_CONNECT_LOCALHOST);
        assertNotNull(englishData.TOO_LONG_LATENCY_MSG);
        assertNotNull(englishData.LOAD);
        assertNotNull(englishData.AS_A_CERTIFICATE);
        assertNotNull(englishData.LISTEN_AT);
        assertNotNull(englishData.NO_FLOW_LEFT);
    }

    @Test
    @DisplayName("testChineseInstanceContainsUpdateManagerMessages")
    void testChineseInstanceContainsUpdateManagerMessages() {
        assertEquals("下载更新文件失败。", chineseData.FAILED_TO_DOWNLOAD_UPDATE_FILE);
        assertEquals("备份现有jar文件失败。", chineseData.FAILED_TO_BACKUP_EXISTING_JAR);
        assertEquals("删除现有jar文件失败。", chineseData.FAILED_TO_DELETE_EXISTING_JAR);
        assertEquals("检查更新失败：", chineseData.FAILED_TO_CHECK_UPDATES);
        assertEquals("更新过程中发生意外错误：", chineseData.UNEXPECTED_ERROR_DURING_UPDATE);
        assertEquals("接收到无效的文件大小：", chineseData.INVALID_FILE_SIZE_RECEIVED);
        assertEquals("正在下载文件，大小：", chineseData.DOWNLOADING_FILE_OF_SIZE);
        assertEquals("连接意外关闭", chineseData.CONNECTION_CLOSED_PREMATURELY);
        assertEquals("下载进度：", chineseData.DOWNLOAD_PROGRESS);
        assertEquals("文件大小不匹配。预期：", chineseData.FILE_SIZE_MISMATCH);
        assertEquals("下载文件时出错：", chineseData.ERROR_WHILE_DOWNLOADING_FILE);
        assertEquals("接收文件时出错：", chineseData.ERROR_RECEIVING_FILE);
        assertEquals("找不到可执行文件或不是文件：", chineseData.EXECUTABLE_NOT_FOUND);
        assertEquals("正在启动更新安装器：", chineseData.STARTING_INSTALLER);
        assertEquals("更新安装器启动成功。", chineseData.INSTALLER_STARTED);
        assertEquals("启动更新安装器失败：", chineseData.FAILED_TO_START_INSTALLER);
        assertEquals("删除失败：", chineseData.FAILED_TO_DELETE);
        assertEquals("成功删除：", chineseData.SUCCESSFULLY_DELETED);
        assertEquals("删除文件时出错：", chineseData.ERROR_DELETING_FILE);
        assertEquals("文件下载成功完成。", chineseData.FILE_DOWNLOAD_COMPLETED);
        assertEquals("更新文件将保存到目录：", chineseData.UPDATE_DOWNLOAD_TARGET);
        assertEquals("更新文件已保存到：", chineseData.UPDATE_SAVED_TO);
        assertEquals("下载已重定向到：", chineseData.DOWNLOAD_REDIRECTED_TO);
        assertEquals("下载失败，服务端返回 HTTP 状态码：", chineseData.DOWNLOAD_FAILED_HTTP_CODE);
        assertEquals("下载重定向缺少 Location 响应头，HTTP 状态码：", chineseData.DOWNLOAD_REDIRECT_WITHOUT_LOCATION);
        assertEquals("下载重定向次数过多，限制：", chineseData.TOO_MANY_DOWNLOAD_REDIRECTS);
        assertEquals("不支持的下载协议：", chineseData.UNSUPPORTED_DOWNLOAD_PROTOCOL);
        assertEquals("无效的下载地址：", chineseData.INVALID_DOWNLOAD_URL);
        assertEquals("无效的下载目标：", chineseData.INVALID_DOWNLOAD_TARGET);
        assertEquals("正在下载文件，大小未知...", chineseData.DOWNLOADING_FILE_SIZE_UNKNOWN);
        assertEquals("TCP 服务已禁用！", chineseData.WARNING_TCP_DISABLED);
        assertEquals("UDP 服务已禁用！", chineseData.WARNING_UDP_DISABLED);
    }

    @Test
    @DisplayName("testChineseInstanceContainsNkmMessages")
    void testChineseInstanceContainsNkmMessages() {
        assertEquals("正在向 NKM 获取最新可用节点列表: ", chineseData.FETCHING_NODE_LIST);
        assertEquals("节点列表已成功更新。", chineseData.NODE_LIST_FETCH_SUCCESS);
        assertEquals("获取节点列表失败或超时 (已跳过): ", chineseData.NODE_LIST_FETCH_FAIL);
        assertEquals("获取到的节点列表格式无效，跳过更新。", chineseData.NODE_LIST_INVALID_JSON);
    }

    @Test
    @DisplayName("testEnglishInstanceContainsNkmMessages")
    void testEnglishInstanceContainsNkmMessages() {
        assertEquals("Fetching latest public node list from NKM: ", englishData.FETCHING_NODE_LIST);
        assertEquals("Node list successfully updated from NKM.", englishData.NODE_LIST_FETCH_SUCCESS);
        assertEquals("Failed to fetch node list (skipped): ", englishData.NODE_LIST_FETCH_FAIL);
        assertEquals("Node list JSON is invalid. Skipping update.", englishData.NODE_LIST_INVALID_JSON);
    }

    @Test
    @DisplayName("testSayReconnectMsgDifferentSeconds")
    void testSayReconnectMsgDifferentSeconds() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            LanguageData data = new LanguageData();
            data.sayReconnectMsg(1);
            assertTrue(outContent.toString().contains("1 seconds"));

            outContent.reset();
            data.sayReconnectMsg(100);
            assertTrue(outContent.toString().contains("100 seconds"));
        } finally {
            System.setOut(originalOut);
        }
    }
}
