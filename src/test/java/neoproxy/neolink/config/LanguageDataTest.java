package neoproxy.neolink.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LanguageData 测试类
 *
 * 测试范围：
 * 1. 默认英文语言实例创建
 * 2. 中文语言实例创建
 * 3. flush 方法语言切换
 * 4. sayReconnectMsg 中英文输出
 * 5. getCurrentLanguage 语言标识
 */
@DisplayName("LanguageData 语言数据测试")
class LanguageDataTest {

    private LanguageData englishData;
    private LanguageData chineseData;

    @BeforeEach
    void setUp() {
        englishData = new LanguageData();
        chineseData = LanguageData.getChineseLanguage();
    }

    @Test
    @DisplayName("默认构造函数应创建英文语言实例")
    void testDefaultConstructorCreatesEnglishInstance() {
        LanguageData data = new LanguageData();

        assertEquals("en", data.getCurrentLanguage());
        assertEquals("The server is offline.", data.SERVER_IS_OFFLINE);
        assertEquals("This should be an integer.", data.IT_MUST_BE_INT);
        assertEquals("The input port range should be between 1~65535.", data.PORT_OUT_OF_RANGE_MSG);
    }

    @Test
    @DisplayName("getChineseLanguage 应创建中文语言实例")
    void testGetChineseLanguageCreatesChineseInstance() {
        LanguageData data = LanguageData.getChineseLanguage();

        assertEquals("zh", data.getCurrentLanguage());
        assertEquals("服务端离线。", data.SERVER_IS_OFFLINE);
        assertEquals("这应该为整数。", data.IT_MUST_BE_INT);
        assertEquals("输入的端口范围应在1~65535之间。", data.PORT_OUT_OF_RANGE_MSG);
    }

    @Test
    @DisplayName("中文实例应正确翻译所有字段")
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
    @DisplayName("flush 方法在英文模式下应返回新的英文实例")
    void testFlushReturnsEnglishInstanceWhenCurrentIsEnglish() {
        LanguageData data = new LanguageData();
        LanguageData flushed = data.flush();

        assertEquals("en", flushed.getCurrentLanguage());
        assertNotSame(data, flushed);
    }

    @Test
    @DisplayName("flush 方法在中文模式下应返回新的中文实例")
    void testFlushReturnsChineseInstanceWhenCurrentIsChinese() {
        LanguageData data = LanguageData.getChineseLanguage();
        LanguageData flushed = data.flush();

        assertEquals("zh", flushed.getCurrentLanguage());
        assertNotSame(data, flushed);
    }

    @Test
    @DisplayName("sayReconnectMsg 英文模式应输出英文消息")
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
    @DisplayName("sayReconnectMsg 中文模式应输出中文消息")
    void testSayReconnectMsgChineseOutput() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            LanguageData data = LanguageData.getChineseLanguage();
            data.sayReconnectMsg(15);

            String output = outContent.toString().trim();
            assertTrue(output.contains("15 秒将会后开始重新连接"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("getCurrentLanguage 应返回正确的语言标识")
    void testGetCurrentLanguage() {
        assertEquals("en", englishData.getCurrentLanguage());
        assertEquals("zh", chineseData.getCurrentLanguage());
    }

    @Test
    @DisplayName("英文实例应包含所有默认消息字段")
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
    @DisplayName("中文实例应包含所有翻译后的更新管理器消息")
    void testChineseInstanceContainsUpdateManagerMessages() {
        assertEquals("下载更新文件失败。", chineseData.FAILED_TO_DOWNLOAD_UPDATE_FILE);
        assertEquals("解压7z文件失败。", chineseData.FAILED_TO_EXTRACT_7Z_FILE);
        assertEquals("在解压的文件中未找到NeoLink.exe。", chineseData.NEOLINK_EXE_NOT_FOUND);
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
        assertEquals("7z文件成功解压到：", chineseData.SEVENZ_FILE_EXTRACTED_SUCCESSFULLY);
        assertEquals("创建目录失败：", chineseData.FAILED_TO_CREATE_DIRECTORY);
        assertEquals("找不到可执行文件或不是文件：", chineseData.EXECUTABLE_NOT_FOUND);
        assertEquals("正在启动新版本，命令：", chineseData.STARTING_NEW_VERSION);
        assertEquals("新版本启动成功。", chineseData.NEW_VERSION_STARTED);
        assertEquals("启动新版本失败：", chineseData.FAILED_TO_START_NEW_VERSION);
        assertEquals("删除失败：", chineseData.FAILED_TO_DELETE);
        assertEquals("成功删除：", chineseData.SUCCESSFULLY_DELETED);
        assertEquals("删除文件时出错：", chineseData.ERROR_DELETING_FILE);
        assertEquals("文件下载成功完成。", chineseData.FILE_DOWNLOAD_COMPLETED);
        assertEquals("关闭7z文件时出错：", chineseData.ERROR_CLOSING_7Z_FILE);
        assertEquals("TCP 服务已禁用！", chineseData.WARNING_TCP_DISABLED);
        assertEquals("UDP 服务已禁用！", chineseData.WARNING_UDP_DISABLED);
    }

    @Test
    @DisplayName("中文实例应包含 NKM 节点拉取相关翻译")
    void testChineseInstanceContainsNkmMessages() {
        assertEquals("正在向 NKM 获取最新可用节点列表: ", chineseData.FETCHING_NODE_LIST);
        assertEquals("节点列表已成功更新。", chineseData.NODE_LIST_FETCH_SUCCESS);
        assertEquals("获取节点列表失败或超时 (已跳过): ", chineseData.NODE_LIST_FETCH_FAIL);
        assertEquals("获取到的节点列表格式无效，跳过更新。", chineseData.NODE_LIST_INVALID_JSON);
    }

    @Test
    @DisplayName("英文实例应包含 NKM 节点拉取相关英文消息")
    void testEnglishInstanceContainsNkmMessages() {
        assertEquals("Fetching latest public node list from NKM: ", englishData.FETCHING_NODE_LIST);
        assertEquals("Node list successfully updated from NKM.", englishData.NODE_LIST_FETCH_SUCCESS);
        assertEquals("Failed to fetch node list (skipped): ", englishData.NODE_LIST_FETCH_FAIL);
        assertEquals("Node list JSON is invalid. Skipping update.", englishData.NODE_LIST_INVALID_JSON);
    }

    @Test
    @DisplayName("sayReconnectMsg 不同秒数应正确输出")
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
