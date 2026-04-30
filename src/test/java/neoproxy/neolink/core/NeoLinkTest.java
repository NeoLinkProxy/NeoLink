package neoproxy.neolink.core;

import top.ceroxe.api.print.log.LogType;
import top.ceroxe.api.print.log.Loggist;
import top.ceroxe.api.print.log.State;
import top.ceroxe.api.neolink.exception.NoMoreNetworkFlowException;
import top.ceroxe.api.neolink.exception.NoSuchKeyException;
import top.ceroxe.api.neolink.exception.UnsupportedVersionException;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.NodeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NeoLink 测试类
 *
 * 测试范围：
 * 1. 语言检测
 * 2. 客户端信息格式化
 * 3. 常量验证
 * 4. 日志输出
 * 5. 自动启动配置
 * 6. 命令行参数解析
 * 7. 节点配置加载
 */
@DisplayName("NeoLink 主类测试")
class NeoLinkTest {

    private Locale originalDefaultLocale;
    private LanguageData originalLanguageData;
    private boolean originalDisableTCP;
    private boolean originalDisableUDP;
    private boolean originalTestUpdate;
    private Loggist originalLoggist;
    private boolean originalShowConnection;
    private boolean originalIsGUIMode;
    private boolean originalEnableAutoReconnect;
    private String originalKey;
    private int originalLocalPort;
    private String originalRemoteDomainName;
    private String originalLocalDomainName;
    private int originalHostHookPort;
    private int originalHostConnectPort;
    private Scanner originalInputScanner;
    private boolean originalIsDebugMode;
    private String originalOutputFilePath;
    private boolean originalEnableAutoUpdate;
    private boolean originalEnableProxyProtocol;
    private int originalReconnectionIntervalSeconds;
    private String originalSpecifiedNodeName;
    private String originalNkmNodeListUrl;
    private int originalRemotePort;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        originalDefaultLocale = Locale.getDefault();
        originalLanguageData = NeoLink.languageData;
        originalDisableTCP = NeoLink.isDisableTCP;
        originalDisableUDP = NeoLink.isDisableUDP;
        originalTestUpdate = NeoLink.isTestUpdate;
        originalLoggist = NeoLink.loggist;
        originalShowConnection = NeoLink.showConnection;
        originalIsGUIMode = NeoLink.isGUIMode;
        originalEnableAutoReconnect = NeoLink.enableAutoReconnect;
        originalKey = NeoLink.key;
        originalLocalPort = NeoLink.localPort;
        originalRemoteDomainName = NeoLink.remoteDomainName;
        originalLocalDomainName = NeoLink.localDomainName;
        originalHostHookPort = NeoLink.hostHookPort;
        originalHostConnectPort = NeoLink.hostConnectPort;
        originalInputScanner = NeoLink.inputScanner;
        originalIsDebugMode = NeoLink.isDebugMode;
        originalOutputFilePath = NeoLink.outputFilePath;
        originalEnableAutoUpdate = NeoLink.enableAutoUpdate;
        originalEnableProxyProtocol = NeoLink.enableProxyProtocol;
        originalReconnectionIntervalSeconds = NeoLink.reconnectionIntervalSeconds;
        originalSpecifiedNodeName = NeoLink.specifiedNodeName;
        originalNkmNodeListUrl = NeoLink.nkmNodeListUrl;
        originalRemotePort = NeoLink.remotePort;
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalDefaultLocale);
        NeoLink.languageData = originalLanguageData;
        NeoLink.isDisableTCP = originalDisableTCP;
        NeoLink.isDisableUDP = originalDisableUDP;
        NeoLink.isTestUpdate = originalTestUpdate;
        NeoLink.loggist = originalLoggist;
        NeoLink.showConnection = originalShowConnection;
        NeoLink.isGUIMode = originalIsGUIMode;
        NeoLink.enableAutoReconnect = originalEnableAutoReconnect;
        NeoLink.key = originalKey;
        NeoLink.localPort = originalLocalPort;
        NeoLink.remoteDomainName = originalRemoteDomainName;
        NeoLink.localDomainName = originalLocalDomainName;
        NeoLink.hostHookPort = originalHostHookPort;
        NeoLink.hostConnectPort = originalHostConnectPort;
        NeoLink.inputScanner = originalInputScanner;
        NeoLink.isDebugMode = originalIsDebugMode;
        NeoLink.outputFilePath = originalOutputFilePath;
        NeoLink.enableAutoUpdate = originalEnableAutoUpdate;
        NeoLink.enableProxyProtocol = originalEnableProxyProtocol;
        NeoLink.reconnectionIntervalSeconds = originalReconnectionIntervalSeconds;
        NeoLink.specifiedNodeName = originalSpecifiedNodeName;
        NeoLink.nkmNodeListUrl = originalNkmNodeListUrl;
        NeoLink.remotePort = originalRemotePort;
    }

    @Test
    @DisplayName("CLIENT_FILE_PREFIX 应为 NeoLink-")
    void testClientFilePrefixConstant() {
        assertEquals("NeoLink-", NeoLink.CLIENT_FILE_PREFIX);
    }

    @Test
    @DisplayName("TEST_UPDATE_VERSION 应为服务端可判定过旧的低版本号")
    void testTestUpdateVersionConstant() {
        assertEquals("0.0.1", NeoLink.TEST_UPDATE_VERSION);
    }

    @Test
    @DisplayName("INVALID_LOCAL_PORT 应为 -1")
    void testInvalidLocalPortConstant() {
        assertEquals(-1, NeoLink.INVALID_LOCAL_PORT);
    }

    @Test
    @DisplayName("detectLanguage 在中文环境下应返回中文语言数据")
    void testDetectLanguageChinese() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        NeoLink.languageData = null;

        NeoLink.detectLanguage();

        assertNotNull(NeoLink.languageData);
        assertEquals("zh", NeoLink.languageData.getCurrentLanguage());
    }

    @Test
    @DisplayName("detectLanguage 在英文环境下应返回英文语言数据")
    void testDetectLanguageEnglish() {
        Locale.setDefault(Locale.US);
        NeoLink.languageData = null;

        NeoLink.detectLanguage();

        assertNotNull(NeoLink.languageData);
        assertEquals("en", NeoLink.languageData.getCurrentLanguage());
    }

    @Test
    @DisplayName("detectLanguage 在语言数据已存在时不应覆盖")
    void testDetectLanguageDoesNotOverride() {
        NeoLink.languageData = LanguageData.getChineseLanguage();

        NeoLink.detectLanguage();

        assertEquals("zh", NeoLink.languageData.getCurrentLanguage());
    }

    @Test
    @DisplayName("getClientVersionToReport 默认应返回真实版本号")
    void testGetClientVersionToReportDefaultVersion() {
        NeoLink.isTestUpdate = false;

        assertEquals(VersionInfo.VERSION, NeoLink.getClientVersionToReport());
    }

    @Test
    @DisplayName("getClientVersionToReport 测试更新模式应返回低版本号")
    void testGetClientVersionToReportTestUpdateVersion() {
        NeoLink.isTestUpdate = true;

        assertEquals(NeoLink.TEST_UPDATE_VERSION, NeoLink.getClientVersionToReport());
    }

    @Test
    @DisplayName("ASCII_LOGO 应为非空字符串")
    void testAsciiLogoNotEmpty() {
        assertNotNull(NeoLink.ASCII_LOGO);
        assertFalse(NeoLink.ASCII_LOGO.isEmpty());
    }

    @Test
    @DisplayName("ASCII_LOGO 应包含 ASCII 艺术字特征")
    void testAsciiLogoContainsAsciiArt() {
        assertTrue(NeoLink.ASCII_LOGO.contains("_") || NeoLink.ASCII_LOGO.contains("|") || NeoLink.ASCII_LOGO.contains("/"));
    }

    @Test
    @DisplayName("CURRENT_DIR_PATH 应为系统属性 user.dir")
    void testCurrentDirPathConstant() {
        assertEquals(System.getProperty("user.dir"), NeoLink.CURRENT_DIR_PATH);
    }

    @Test
    @DisplayName("shouldAutoStart 默认应为 false")
    void testShouldAutoStartDefault() throws Exception {
        Field field = NeoLink.class.getDeclaredField("shouldAutoStartInGUI");
        field.setAccessible(true);
        field.setBoolean(null, false);

        assertFalse(NeoLink.shouldAutoStart());
    }

    @Test
    @DisplayName("shouldAutoStart 设置为 true 时应返回 true")
    void testShouldAutoStartTrue() throws Exception {
        Field field = NeoLink.class.getDeclaredField("shouldAutoStartInGUI");
        field.setAccessible(true);
        field.setBoolean(null, true);

        assertTrue(NeoLink.shouldAutoStart());
    }

    @Test
    @DisplayName("say 方法在 loggit 为 null 时应输出到控制台")
    void testSayWithNullLoggit() {
        NeoLink.loggist = null;

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            NeoLink.say("Test message");
            assertTrue(outContent.toString().contains("[LOG-PENDING] Test message"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("say 方法在 loggist 不为 null 时应调用 loggist")
    void testSayWithLoggit() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;

        NeoLink.say("Test message");

        verify(mockLoggist).say(any(State.class));
    }

    @Test
    @DisplayName("say(LogType) 方法在 loggist 为 null 时应输出到控制台")
    void testSayWithLogTypeAndNullLoggit() {
        NeoLink.loggist = null;

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            NeoLink.say("Test warning", LogType.WARNING);
            assertTrue(outContent.toString().contains("[LOG-PENDING] Test warning"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("say(LogType) 方法在 loggist 不为 null 时应调用 loggist")
    void testSayWithLogTypeAndLoggit() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;

        NeoLink.say("Test warning", LogType.WARNING);

        verify(mockLoggist).say(any(State.class));
    }

    @Test
    @DisplayName("printLogo 应调用 say 方法")
    void testPrintLogo() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;

        NeoLink.printLogo();

        verify(mockLoggist).say(any(State.class));
    }

    @Test
    @DisplayName("printBasicInfo 应输出版本信息")
    void testPrintBasicInfo() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;
        NeoLink.languageData = new LanguageData();
        NeoLink.isDisableTCP = false;
        NeoLink.isDisableUDP = false;

        NeoLink.printBasicInfo();

        verify(mockLoggist, atLeastOnce()).say(any(State.class));
    }

    @Test
    @DisplayName("printBasicInfo 测试更新模式应输出与握手一致的低版本号")
    void testPrintBasicInfoUsesTestUpdateVersion() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;
        NeoLink.languageData = new LanguageData();
        NeoLink.isTestUpdate = true;
        NeoLink.isDisableTCP = false;
        NeoLink.isDisableUDP = false;

        NeoLink.printBasicInfo();

        verify(mockLoggist).say(argThat(state ->
                state != null && (NeoLink.languageData.VERSION + NeoLink.TEST_UPDATE_VERSION).equals(state.getContent())
        ));
    }

    @Test
    @DisplayName("printBasicInfo TCP 禁用时应输出警告")
    void testPrintBasicInfoTcpDisabled() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;
        NeoLink.languageData = new LanguageData();
        NeoLink.isDisableTCP = true;
        NeoLink.isDisableUDP = false;

        NeoLink.printBasicInfo();

        verify(mockLoggist, atLeast(2)).say(any(State.class));
    }

    @Test
    @DisplayName("printBasicInfo UDP 禁用时应输出警告")
    void testPrintBasicInfoUdpDisabled() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;
        NeoLink.languageData = new LanguageData();
        NeoLink.isDisableTCP = false;
        NeoLink.isDisableUDP = true;

        NeoLink.printBasicInfo();

        verify(mockLoggist, atLeast(2)).say(any(State.class));
    }

    @Test
    @DisplayName("printBasicInfo TCP 和 UDP 都禁用时应输出两个警告")
    void testPrintBasicInfoBothDisabled() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;
        NeoLink.languageData = new LanguageData();
        NeoLink.isDisableTCP = true;
        NeoLink.isDisableUDP = true;

        NeoLink.printBasicInfo();

        verify(mockLoggist, atLeast(3)).say(any(State.class));
    }

    @Test
    @DisplayName("sayInfoNoNewLine 应调用 loggist.sayNoNewLine")
    void testSayInfoNoNewLine() {
        Loggist mockLoggist = mock(Loggist.class);
        NeoLink.loggist = mockLoggist;

        NeoLink.sayInfoNoNewLine("Test message");

        verify(mockLoggist).sayNoNewLine(any(State.class));
    }

    @Test
    @DisplayName("API 业务异常应转换为客户端展示文案")
    void testClientFacingApiErrorMessageUsesBusinessTextOnly() {
        NeoLink.languageData = LanguageData.getChineseLanguage();

        assertEquals(
                "密钥错误，强制退出。。。",
                NeoLinkCoreRunner.clientFacingApiErrorMessage(
                        "NeoProxyServer rejected the access key: 密钥错误，强制退出。。。",
                        new NoSuchKeyException("密钥错误，强制退出。。。")
                )
        );
        assertEquals(
                "没有多余的流量了。",
                NeoLinkCoreRunner.clientFacingApiErrorMessage(
                        "NeoProxyServer terminated the tunnel because no network flow remains: exitNoFlow",
                        new NoMoreNetworkFlowException()
                )
        );
        assertEquals(
                "Unsupported version:6.0.1",
                NeoLinkCoreRunner.clientFacingApiErrorMessage(
                        "NeoProxyServer does not support this NeoLinkAPI version: Unsupported version:6.0.1",
                        new UnsupportedVersionException("Unsupported version:6.0.1")
                )
        );
    }

    @Test
    @DisplayName("API 回调错误不应默认输出底层异常到 GUI")
    void testClientFacingCallbackErrorMessageSuppressesInfrastructureMessages() {
        NeoLink.languageData = LanguageData.getChineseLanguage();

        assertNull(NeoLinkCoreRunner.clientFacingCallbackErrorMessage(
                "NeoProxyServer rejected the access key: 密钥错误，强制退出。。。",
                new NoSuchKeyException("密钥错误，强制退出。。。"),
                false
        ));
        assertNull(NeoLinkCoreRunner.clientFacingCallbackErrorMessage(
                "连接本地服务失败：localhost:7777",
                new IOException("Connection refused: getsockopt"),
                true
        ));
        assertEquals(
                "没有多余的流量了。",
                NeoLinkCoreRunner.clientFacingCallbackErrorMessage(
                        "NeoProxyServer terminated the tunnel because no network flow remains: exitNoFlow",
                        new NoMoreNetworkFlowException(),
                        true
                )
        );
    }

    @Test
    @DisplayName("getCurrentFile 应返回文件对象或 null")
    void testGetCurrentFile() {
        var file = NeoLink.getCurrentFile();
        assertTrue(file == null || file instanceof java.io.File);
    }

    @Test
    @DisplayName("remotePort 默认应为 0")
    void testRemotePortDefaultAfterShellMigration() {
        assertEquals(0, NeoLink.remotePort);
    }

    @Test
    @DisplayName("hostHookPort 默认应为 44801")
    void testHostHookPortDefault() {
        assertEquals(44801, NeoLink.hostHookPort);
    }

    @Test
    @DisplayName("hostConnectPort 默认应为 44802")
    void testHostConnectPortDefault() {
        assertEquals(44802, NeoLink.hostConnectPort);
    }

    @Test
    @DisplayName("remoteDomainName 默认应为 localhost")
    void testRemoteDomainNameDefault() {
        assertEquals("localhost", NeoLink.remoteDomainName);
    }

    @Test
    @DisplayName("localDomainName 默认应为 localhost")
    void testLocalDomainNameDefault() {
        assertEquals("localhost", NeoLink.localDomainName);
    }

    @Test
    @DisplayName("isGUIMode 默认应为 true")
    void testIsGUIModeDefault() {
        assertTrue(NeoLink.isGUIMode);
    }

    @Test
    @DisplayName("showConnection 默认应为 true")
    void testShowConnectionDefault() {
        assertTrue(NeoLink.showConnection);
    }

    @Test
    @DisplayName("enableAutoReconnect 默认应为 true")
    void testEnableAutoReconnectDefault() {
        assertTrue(NeoLink.enableAutoReconnect);
    }

    @Test
    @DisplayName("enableAutoUpdate 默认应为 true")
    void testEnableAutoUpdateDefault() {
        assertTrue(NeoLink.enableAutoUpdate);
    }

    @Test
    @DisplayName("enableProxyProtocol 默认应为 false")
    void testEnableProxyProtocolDefault() {
        assertFalse(NeoLink.enableProxyProtocol);
    }

    @Test
    @DisplayName("reconnectionIntervalSeconds 默认应为 30")
    void testReconnectionIntervalSecondsDefault() {
        assertEquals(30, NeoLink.reconnectionIntervalSeconds);
    }

    @Test
    @DisplayName("isDebugMode 默认应为 false")
    void testIsDebugModeDefault() {
        assertFalse(NeoLink.isDebugMode);
    }

    @Test
    @DisplayName("isReconnectedOperation 默认应为 false")
    void testIsReconnectedOperationDefault() {
        assertFalse(NeoLink.isReconnectedOperation);
    }

    @Test
    @DisplayName("parseCommandLineArgs --key 参数应设置 key")
    void testParseCommandLineArgsKey() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.key = null;
        method.invoke(null, (Object) new String[]{"--key=test-key-123"});

        assertEquals("test-key-123", NeoLink.key);
    }

    @Test
    @DisplayName("parseCommandLineArgs --local-port 参数应设置 localPort")
    void testParseCommandLineArgsLocalPort() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.localPort = NeoLink.INVALID_LOCAL_PORT;
        method.invoke(null, (Object) new String[]{"--local-port=8080"});

        assertEquals(8080, NeoLink.localPort);
    }

    @Test
    @DisplayName("parseCommandLineArgs --output-file 参数应设置 outputFilePath")
    void testParseCommandLineArgsOutputFile() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.outputFilePath = null;
        method.invoke(null, (Object) new String[]{"--output-file=/path/to/output"});

        assertEquals("/path/to/output", NeoLink.outputFilePath);
    }

    @Test
    @DisplayName("parseCommandLineArgs --node 参数应设置 specifiedNodeName")
    void testParseCommandLineArgsNode() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.specifiedNodeName = null;
        method.invoke(null, (Object) new String[]{"--node=my-node"});

        assertEquals("my-node", NeoLink.specifiedNodeName);
    }

    @Test
    @DisplayName("parseCommandLineArgs --debug 标志应设置 isDebugMode")
    void testParseCommandLineArgsDebug() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.isDebugMode = false;
        method.invoke(null, (Object) new String[]{"--debug"});

        assertTrue(NeoLink.isDebugMode);
    }

    @Test
    @DisplayName("parseCommandLineArgs --nogui 标志应设置 isGUIMode 为 false")
    void testParseCommandLineArgsNoGui() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.isGUIMode = true;
        method.invoke(null, (Object) new String[]{"--nogui"});

        assertFalse(NeoLink.isGUIMode);
    }

    @Test
    @DisplayName("parseCommandLineArgs --gui 标志应设置 isGUIMode 为 true")
    void testParseCommandLineArgsGui() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.isGUIMode = false;
        method.invoke(null, (Object) new String[]{"--gui"});

        assertTrue(NeoLink.isGUIMode);
    }

    @Test
    @DisplayName("parseCommandLineArgs --disable-tcp 标志应设置 isDisableTCP")
    void testParseCommandLineArgsDisableTcp() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.isDisableTCP = false;
        method.invoke(null, (Object) new String[]{"--disable-tcp"});

        assertTrue(NeoLink.isDisableTCP);
    }

    @Test
    @DisplayName("parseCommandLineArgs --disable-udp 标志应设置 isDisableUDP")
    void testParseCommandLineArgsDisableUdp() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.isDisableUDP = false;
        method.invoke(null, (Object) new String[]{"--disable-udp"});

        assertTrue(NeoLink.isDisableUDP);
    }

    @Test
    @DisplayName("parseCommandLineArgs --enable-pp 标志应设置 enableProxyProtocol")
    void testParseCommandLineArgsEnablePp() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.enableProxyProtocol = false;
        method.invoke(null, (Object) new String[]{"--enable-pp"});

        assertTrue(NeoLink.enableProxyProtocol);
    }

    @Test
    @DisplayName("parseCommandLineArgs --test-update 标志应设置 isTestUpdate")
    void testParseCommandLineArgsTestUpdate() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.isTestUpdate = false;
        method.invoke(null, (Object) new String[]{"--test-update"});

        assertTrue(NeoLink.isTestUpdate);
    }

    @Test
    @DisplayName("parseCommandLineArgs --en-us 标志应设置英文语言数据")
    void testParseCommandLineArgsEnUs() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.languageData = null;
        method.invoke(null, (Object) new String[]{"--en-us"});

        assertNotNull(NeoLink.languageData);
        assertEquals("en", NeoLink.languageData.getCurrentLanguage());
    }

    @Test
    @DisplayName("parseCommandLineArgs --zh-cn 标志应设置中文语言数据")
    void testParseCommandLineArgsZhCn() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.languageData = null;
        method.invoke(null, (Object) new String[]{"--zh-cn"});

        assertNotNull(NeoLink.languageData);
        assertEquals("zh", NeoLink.languageData.getCurrentLanguage());
    }

    @Test
    @DisplayName("parseCommandLineArgs --no-show-conn 标志应设置 showConnection 为 false")
    void testParseCommandLineArgsNoShowConn() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        NeoLink.showConnection = true;
        method.invoke(null, (Object) new String[]{"--no-show-conn"});

        assertFalse(NeoLink.showConnection);
    }

    @Test
    @DisplayName("parseCommandLineArgs --no-color 标志应设置 noColor")
    void testParseCommandLineArgsNoColor() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        Field noColorField = NeoLink.class.getDeclaredField("noColor");
        noColorField.setAccessible(true);
        noColorField.setBoolean(null, false);

        method.invoke(null, (Object) new String[]{"--no-color"});

        assertTrue(noColorField.getBoolean(null));
    }

    @Test
    @DisplayName("parseCommandLineArgs 同时有 key 和 local-port 且 GUI 模式应设置 shouldAutoStartInGUI")
    void testParseCommandLineArgsAutoStart() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        method.setAccessible(true);

        Field shouldAutoStartField = NeoLink.class.getDeclaredField("shouldAutoStartInGUI");
        shouldAutoStartField.setAccessible(true);
        shouldAutoStartField.setBoolean(null, false);

        NeoLink.isGUIMode = true;
        NeoLink.key = null;
        NeoLink.localPort = NeoLink.INVALID_LOCAL_PORT;

        method.invoke(null, (Object) new String[]{"--key=test", "--local-port=8080"});

        assertTrue(shouldAutoStartField.getBoolean(null));
    }

    @Test
    @DisplayName("loadNodeConfiguration 不存在的文件应安全处理")
    void testLoadNodeConfigurationNonExistentFile() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("loadNodeConfiguration");
        method.setAccessible(true);

        NeoLink.specifiedNodeName = "test-node";

        assertDoesNotThrow(() -> method.invoke(null));
    }

    @Test
    @DisplayName("loadNodeConfiguration 存在的文件应正确解析")
    void testLoadNodeConfigurationExistingFile() throws Exception {
        String jsonContent = "[{\"name\":\"test-node\",\"address\":\"test.example.com\",\"HOST_HOOK_PORT\":44801,\"HOST_CONNECT_PORT\":44802}]";
        File nodeFile = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        Files.writeString(nodeFile.toPath(), jsonContent);

        Method method = NeoLink.class.getDeclaredMethod("loadNodeConfiguration");
        method.setAccessible(true);

        NeoLink.specifiedNodeName = "test-node";
        NeoLink.remoteDomainName = "localhost";
        NeoLink.hostHookPort = 44801;
        NeoLink.hostConnectPort = 44802;

        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        method.invoke(null);

        assertEquals("test.example.com", NeoLink.remoteDomainName);
    }

    @Test
    @DisplayName("initializeLogger 应创建日志目录和文件")
    void testInitializeLogger() throws Exception {
        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        NeoLink.loggist = null;

        NeoLink.initializeLogger();

        assertNotNull(NeoLink.loggist);

        File logsDir = new File(tempDir, "logs");
        assertTrue(logsDir.exists());
    }

    @Test
    @DisplayName("inputScanner 应为 Scanner 实例")
    void testInputScannerNotNull() {
        assertNotNull(NeoLink.inputScanner);
    }

    @Test
    @DisplayName("parseKeyValueArgument --output-file 应设置 outputFilePath")
    void testParseKeyValueArgumentOutputFile() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseKeyValueArgument", String.class);
        method.setAccessible(true);

        NeoLink.outputFilePath = null;
        method.invoke(null, "--output-file=/path/to/output.txt");

        assertEquals("/path/to/output.txt", NeoLink.outputFilePath);
    }

    @Test
    @DisplayName("parseKeyValueArgument --node 应设置 specifiedNodeName")
    void testParseKeyValueArgumentNode() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseKeyValueArgument", String.class);
        method.setAccessible(true);

        NeoLink.specifiedNodeName = null;
        method.invoke(null, "--node=test-node");

        assertEquals("test-node", NeoLink.specifiedNodeName);
    }

    @Test
    @DisplayName("parseFlagArgument --en-us 应设置英文语言")
    void testParseFlagArgumentEnUs() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.languageData = null;
        method.invoke(null, "--en-us");

        assertNotNull(NeoLink.languageData);
        assertEquals("en", NeoLink.languageData.getCurrentLanguage());
    }

    @Test
    @DisplayName("parseFlagArgument --zh-cn 应设置中文语言")
    void testParseFlagArgumentZhCn() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.languageData = null;
        method.invoke(null, "--zh-cn");

        assertNotNull(NeoLink.languageData);
        assertEquals("zh", NeoLink.languageData.getCurrentLanguage());
    }

    @Test
    @DisplayName("parseFlagArgument --debug 应设置 isDebugMode")
    void testParseFlagArgumentDebug() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.isDebugMode = false;
        method.invoke(null, "--debug");

        assertTrue(NeoLink.isDebugMode);
    }

    @Test
    @DisplayName("parseFlagArgument --gui 应设置 isGUIMode")
    void testParseFlagArgumentGui() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.isGUIMode = false;
        method.invoke(null, "--gui");

        assertTrue(NeoLink.isGUIMode);
    }

    @Test
    @DisplayName("parseFlagArgument --nogui 应设置 isGUIMode 为 false")
    void testParseFlagArgumentNoGui() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.isGUIMode = true;
        method.invoke(null, "--nogui");

        assertFalse(NeoLink.isGUIMode);
    }

    @Test
    @DisplayName("parseFlagArgument --disable-tcp 应设置 isDisableTCP")
    void testParseFlagArgumentDisableTcp() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.isDisableTCP = false;
        method.invoke(null, "--disable-tcp");

        assertTrue(NeoLink.isDisableTCP);
    }

    @Test
    @DisplayName("parseFlagArgument --disable-udp 应设置 isDisableUDP")
    void testParseFlagArgumentDisableUdp() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.isDisableUDP = false;
        method.invoke(null, "--disable-udp");

        assertTrue(NeoLink.isDisableUDP);
    }

    @Test
    @DisplayName("parseFlagArgument --enable-pp 应设置 enableProxyProtocol")
    void testParseFlagArgumentEnablePp() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.enableProxyProtocol = false;
        method.invoke(null, "--enable-pp");

        assertTrue(NeoLink.enableProxyProtocol);
    }

    @Test
    @DisplayName("parseFlagArgument --test-update 应设置 isTestUpdate")
    void testParseFlagArgumentTestUpdate() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.isTestUpdate = false;
        method.invoke(null, "--test-update");

        assertTrue(NeoLink.isTestUpdate);
    }

    @Test
    @DisplayName("parseFlagArgument --no-show-conn 应设置 showConnection 为 false")
    void testParseFlagArgumentNoShowConn() throws Exception {
        Method method = NeoLink.class.getDeclaredMethod("parseFlagArgument", String.class);
        method.setAccessible(true);

        NeoLink.showConnection = true;
        method.invoke(null, "--no-show-conn");

        assertFalse(NeoLink.showConnection);
    }

    @Test
    @DisplayName("initializeLogger 有 noColor 标志时应禁用颜色")
    void testInitializeLoggerWithNoColor() throws Exception {
        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        Field noColorField = NeoLink.class.getDeclaredField("noColor");
        noColorField.setAccessible(true);
        noColorField.setBoolean(null, true);

        NeoLink.loggist = null;

        NeoLink.initializeLogger();

        assertNotNull(NeoLink.loggist);

        noColorField.setBoolean(null, false);
    }

    @Test
    @DisplayName("loadNodeConfiguration 应解析 HOST_HOOK_PORT 和 HOST_CONNECT_PORT")
    void testLoadNodeConfigurationPortParsing() throws Exception {
        String jsonContent = "[{\"name\":\"test-node\",\"address\":\"test.example.com\",\"HOST_HOOK_PORT\":44901,\"HOST_CONNECT_PORT\":44902}]";
        File nodeFile = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        Files.writeString(nodeFile.toPath(), jsonContent);

        Method method = NeoLink.class.getDeclaredMethod("loadNodeConfiguration");
        method.setAccessible(true);

        NeoLink.specifiedNodeName = "test-node";
        NeoLink.remoteDomainName = "localhost";
        NeoLink.hostHookPort = 44801;
        NeoLink.hostConnectPort = 44802;

        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        method.invoke(null);

        assertEquals("test.example.com", NeoLink.remoteDomainName);
        assertEquals(44901, NeoLink.hostHookPort);
        assertEquals(44902, NeoLink.hostConnectPort);
    }

    @Test
    @DisplayName("loadNodeConfiguration 应支持使用 NeoKeyManager realId 选择节点")
    void testLoadNodeConfigurationByRealId() throws Exception {
        String jsonContent = "[{\"realId\":\"node-suqian\",\"name\":\"中国 - 宿迁官方\",\"address\":\"p.ceroxe.fun\",\"HOST_HOOK_PORT\":44901,\"HOST_CONNECT_PORT\":44902}]";
        File nodeFile = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        Files.writeString(nodeFile.toPath(), jsonContent);

        Method method = NeoLink.class.getDeclaredMethod("loadNodeConfiguration");
        method.setAccessible(true);

        NeoLink.specifiedNodeName = "node-suqian";
        NeoLink.remoteDomainName = "localhost";
        NeoLink.hostHookPort = 44801;
        NeoLink.hostConnectPort = 44802;

        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        method.invoke(null);

        assertEquals("p.ceroxe.fun", NeoLink.remoteDomainName);
        assertEquals(44901, NeoLink.hostHookPort);
        assertEquals(44902, NeoLink.hostConnectPort);
    }

    @Test
    @DisplayName("loadNodeConfiguration 应解析 hookPort 和 connectPort (小写)")
    void testLoadNodeConfigurationLowercasePorts() throws Exception {
        String jsonContent = "[{\"name\":\"test-node\",\"address\":\"test.example.com\",\"hookPort\":44901,\"connectPort\":44902}]";
        File nodeFile = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        Files.writeString(nodeFile.toPath(), jsonContent);

        Method method = NeoLink.class.getDeclaredMethod("loadNodeConfiguration");
        method.setAccessible(true);

        NeoLink.specifiedNodeName = "test-node";
        NeoLink.remoteDomainName = "localhost";
        NeoLink.hostHookPort = 44801;
        NeoLink.hostConnectPort = 44802;

        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        method.invoke(null);

        assertEquals("test.example.com", NeoLink.remoteDomainName);
        assertEquals(44901, NeoLink.hostHookPort);
        assertEquals(44902, NeoLink.hostConnectPort);
    }

    @Test
    @DisplayName("loadNodeConfiguration 节点不存在时应安全处理")
    void testLoadNodeConfigurationNodeNotFound() throws Exception {
        String jsonContent = "[{\"name\":\"other-node\",\"address\":\"other.example.com\"}]";
        File nodeFile = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        Files.writeString(nodeFile.toPath(), jsonContent);

        Method method = NeoLink.class.getDeclaredMethod("loadNodeConfiguration");
        method.setAccessible(true);

        NeoLink.specifiedNodeName = "non-existent-node";

        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        assertDoesNotThrow(() -> method.invoke(null));
    }

    @Test
    @DisplayName("loadNodeConfiguration 空 JSON 应安全处理")
    void testLoadNodeConfigurationEmptyJson() throws Exception {
        String jsonContent = "[]";
        File nodeFile = new File(tempDir, NodeConfig.NODE_LIST_FILE_NAME);
        Files.writeString(nodeFile.toPath(), jsonContent);

        Method method = NeoLink.class.getDeclaredMethod("loadNodeConfiguration");
        method.setAccessible(true);

        NeoLink.specifiedNodeName = "test-node";

        var workingDirField = neoproxy.neolink.config.ConfigOperator.class.getDeclaredField("WORKING_DIR");
        workingDirField.setAccessible(true);
        workingDirField.set(null, tempDir.getAbsolutePath());

        assertDoesNotThrow(() -> method.invoke(null));
    }

    @Test
    @DisplayName("detectLanguage 中文系统应设置中文语言")
    void testDetectLanguageChineseSystem() {
        NeoLink.languageData = null;

        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.CHINESE);

        NeoLink.detectLanguage();

        assertNotNull(NeoLink.languageData);
        assertEquals("zh", NeoLink.languageData.getCurrentLanguage());

        Locale.setDefault(originalLocale);
    }

    @Test
    @DisplayName("detectLanguage 英文系统应设置英文语言")
    void testDetectLanguageEnglishSystem() {
        NeoLink.languageData = null;

        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);

        NeoLink.detectLanguage();

        assertNotNull(NeoLink.languageData);
        assertEquals("en", NeoLink.languageData.getCurrentLanguage());

        Locale.setDefault(originalLocale);
    }

    @Test
    @DisplayName("detectLanguage 已设置语言数据时不应覆盖")
    void testDetectLanguageAlreadySet() {
        LanguageData originalData = new LanguageData();
        NeoLink.languageData = originalData;

        NeoLink.detectLanguage();

        assertSame(originalData, NeoLink.languageData);
    }

    @Test
    @DisplayName("key 默认应为 null")
    void testKeyDefault() {
        assertNull(NeoLink.key);
    }

    @Test
    @DisplayName("localPort 默认应为 INVALID_LOCAL_PORT")
    void testLocalPortDefault() {
        assertEquals(NeoLink.INVALID_LOCAL_PORT, NeoLink.localPort);
    }

    @Test
    @DisplayName("remotePort 默认应为 0")
    void testRemotePortDefault() {
        assertEquals(0, NeoLink.remotePort);
    }

    @Test
    @DisplayName("isDisableTCP 默认应为 false")
    void testIsDisableTCPDefault() {
        assertFalse(NeoLink.isDisableTCP);
    }

    @Test
    @DisplayName("isDisableUDP 默认应为 false")
    void testIsDisableUDPDefault() {
        assertFalse(NeoLink.isDisableUDP);
    }

    @Test
    @DisplayName("specifiedNodeName 默认应为 null")
    void testSpecifiedNodeNameDefault() {
        assertNull(NeoLink.specifiedNodeName);
    }

    @Test
    @DisplayName("isTestUpdate 默认应为 false")
    void testIsTestUpdateDefault() {
        assertFalse(NeoLink.isTestUpdate);
    }

    @Test
    @DisplayName("nkmNodeListUrl 默认应为空字符串")
    void testNkmNodeListUrlDefault() {
        assertEquals("", NeoLink.nkmNodeListUrl);
    }
}
