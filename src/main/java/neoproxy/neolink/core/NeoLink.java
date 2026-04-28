package neoproxy.neolink.core;

import fun.ceroxe.api.OshiUtils;
import fun.ceroxe.api.net.SecureSocket;
import fun.ceroxe.api.net.TcpPingUtil;
import fun.ceroxe.api.print.log.LogType;
import fun.ceroxe.api.print.log.Loggist;
import fun.ceroxe.api.print.log.State;
import fun.ceroxe.api.thread.ThreadManager;
import fun.ceroxe.api.utils.Sleeper;
import fun.ceroxe.api.utils.TimeUtils;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.gui.ComposeEntryKt;
import neoproxy.neolink.network.InternetOperator;
import neoproxy.neolink.network.NodeFetcher;
import neoproxy.neolink.network.ProxyOperator;
import neoproxy.neolink.network.threads.CheckAliveThread;
import neoproxy.neolink.network.threads.TCPTransformer;
import neoproxy.neolink.network.threads.UDPTransformer;
import neoproxy.neolink.update.UpdateManager;
import neoproxy.neolink.util.Debugger;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

import static neoproxy.neolink.util.Debugger.debugOperation;
import static neoproxy.neolink.network.InternetOperator.*;
import static neoproxy.neolink.update.UpdateManager.checkUpdate;

/**
 * NeoLink 客户端主类
 *
 * 核心职责：
 * 1. 管理客户端与 Neo 服务器之间的连接生命周期
 * 2. 处理命令行参数解析与配置加载
 * 3. 协调 TCP/UDP 隧道的建立与维护
 * 4. 支持 GUI 模式和 CLI 模式双模式运行
 * 5. 实现自动重连、心跳检测、版本更新等高级功能
 *
 * 架构设计：
 * - 采用静态字段设计，便于跨模块访问核心状态
 * - 使用 SecureSocket 实现加密通信
 * - 通过 ThreadManager 管理并发连接
 *
 * @author NeoProxy Team
 * @version 6.0.0
 */
public class NeoLink {
    public static final String CLIENT_FILE_PREFIX = "NeoLink-";
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final int INVALID_LOCAL_PORT = -1;
    public static volatile long lastReceivedTime = System.currentTimeMillis();
    public static int remotePort;
    public static String remoteDomainName = "localhost";
    public static String localDomainName = "localhost";
    public static int hostHookPort = NodeConfig.DEFAULT_HOST_HOOK_PORT;
    public static int hostConnectPort = NodeConfig.DEFAULT_HOST_CONNECT_PORT;
    public static volatile SecureSocket hookSocket;
    public static volatile Socket connectingSocket = null;
    public static String key = null;
    public static int localPort = INVALID_LOCAL_PORT;
    public static Loggist loggist;
    public static String outputFilePath = null;
    public static LanguageData languageData = null;
    public static boolean isReconnectedOperation = false;
    public static boolean isDebugMode = false;
    public static boolean showConnection = true;
    public static boolean enableAutoReconnect = true;
    public static boolean enableAutoUpdate = true;
    public static boolean enableProxyProtocol = false;
    public static int reconnectionIntervalSeconds = 30;

    public static Scanner inputScanner = new Scanner(System.in, StandardCharsets.UTF_8);
    public static boolean isGUIMode = true;
    public static boolean isDisableUDP = false;
    public static boolean isDisableTCP = false;
    public static String specifiedNodeName = null;
    public static boolean isTestUpdate = false;
    public static String nkmNodeListUrl = "";
    private static boolean shouldAutoStartInGUI = false;
    private static boolean noColor = false;
    public static boolean isNoEffectMode = false;

    public static boolean shouldAutoStart() {
        return shouldAutoStartInGUI;
    }

    public static void main(String[] args) {
        ConfigOperator.initEnvironment();
        try {
            ConfigOperator.readAndSetValue();
            applyCommandLineArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[NeoLink] " + e.getMessage());
            System.exit(-1);
            return;
        }
        debugOperation("Entering main() method.");
        debugOperation("Command line arguments parsed. Mode: " + (isGUIMode ? "GUI" : "CLI") + ", Debug: " + isDebugMode);

        if (isGUIMode) {
            debugOperation("GUI Mode detected. Delegating to ComposeEntryKt.main().");
            ThreadManager.runAsync(NodeFetcher::fetchAndSaveNodes);
            ComposeEntryKt.main(args);
            System.exit(0);
        }

        initializeLogger();
        detectLanguage();
        NodeFetcher.fetchAndSaveNodes();

        if (specifiedNodeName != null) {
            loadNodeConfiguration();
        }

        ProxyOperator.init();

        if (!isReconnectedOperation) {
            printLogo();
            printBasicInfo();
        }

        try {
            promptForAccessKey();
            connectToNeoServer();
            // 确保旧心跳线程完全停止后再发送客户端信息，防止并发问题
            CheckAliveThread.stopThread();
            exchangeClientInfoWithServer();
            CheckAliveThread.startThread();
            promptForLocalPort();
            listenForServerCommands();
        } catch (Exception e) {
            debugOperation(e);
            handleConnectionFailure(e);
        }
    }

    private static void loadNodeConfiguration() {
        debugOperation("Attempting to load configuration for node: " + specifiedNodeName);
        File nodeFile = new File(ConfigOperator.WORKING_DIR, "node.json");

        try {
            if (!nodeFile.exists()) {
                throw new IOException("node.json file not found.");
            }
            NodeConfig node = NodeConfig.findByName(nodeFile, specifiedNodeName);
            if (node == null) {
                throw new IOException("Node not found.");
            }

            remoteDomainName = node.getAddress();
            hostHookPort = node.getHostHookPort();
            hostConnectPort = node.getHostConnectPort();
        } catch (Exception e) {
            debugOperation("Failed to load node config: " + e.getMessage());
        }
    }

    public static void applyCommandLineArgs(String[] args) {
        parseCommandLineArgs(args);
    }

    public static void detectLanguage() {
        if (languageData == null) {
            Locale defaultLocale = Locale.getDefault();
            if (defaultLocale.getLanguage().contains("zh")) {
                languageData = LanguageData.getChineseLanguage();
            } else {
                languageData = new LanguageData();
            }
        }
    }

    private static void parseCommandLineArgs(String[] args) {
        if (args == null) {
            return;
        }
        boolean hasKey = false;
        boolean hasLocalPort = false;
        for (String arg : args) {
            if (arg.contains("=")) {
                parseKeyValueArgument(arg);
                if (arg.startsWith("--key=")) hasKey = true;
                else if (arg.startsWith("--local-port=")) hasLocalPort = true;
            } else {
                parseFlagArgument(arg);
            }
        }
        if (hasKey && hasLocalPort && isGUIMode) shouldAutoStartInGUI = true;
    }

    private static void parseKeyValueArgument(String arg) {
        String[] parts = arg.split("=", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException(parts[0] + " requires a value.");
        }
        switch (parts[0]) {
            case "--key" -> key = parts[1];
            case "--local-port" -> localPort = parsePort(parts[1], "--local-port");
            case "--output-file" -> outputFilePath = parts[1];
            case "--node" -> specifiedNodeName = parts[1];
        }
    }

    private static void parseFlagArgument(String arg) {
        switch (arg) {
            case "--en-us" -> languageData = new LanguageData();
            case "--zh-cn" -> languageData = LanguageData.getChineseLanguage();
            case "--no-color" -> noColor = true;
            case "--debug" -> isDebugMode = true;
            case "--no-show-conn" -> showConnection = false;
            case "--gui" -> isGUIMode = true;
            case "--nogui" -> isGUIMode = false;
            case "--disable-tcp" -> isDisableTCP = true;
            case "--disable-udp" -> isDisableUDP = true;
            case "--enable-pp" -> enableProxyProtocol = true;
            case "--test-update" -> isTestUpdate = true;
            case "--no-effect" -> isNoEffectMode = true;
        }
    }

    public static void initializeLogger() {
        File logsDir = new File(ConfigOperator.WORKING_DIR, "logs");
        if (!logsDir.exists()) logsDir.mkdirs();
        File logFile = resolveLogFile(logsDir);
        loggist = new Loggist(logFile);
        if (noColor) loggist.disableColor();
        loggist.openWriteChannel();
    }

    private static File resolveLogFile(File defaultLogsDir) {
        if (outputFilePath == null || outputFilePath.isBlank()) {
            return new File(defaultLogsDir, TimeUtils.getCurrentTimeAsFileName(false) + ".log");
        }

        File logFile = new File(outputFilePath);
        if (!logFile.isAbsolute()) {
            logFile = new File(ConfigOperator.WORKING_DIR, outputFilePath);
        }

        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Failed to create log directory: " + parent.getAbsolutePath());
        }
        return logFile;
    }

    private static int parsePort(String value, String source) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(source + " must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(source + " must be an integer.", e);
        }
    }

    private static void connectToNeoServer() throws IOException {
        say(languageData.CONNECT_TO + remoteDomainName + languageData.OMITTED);
        if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
            hookSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, hostHookPort);
        } else {
            hookSocket = new SecureSocket(remoteDomainName, hostHookPort);
        }
    }

    public static void exchangeClientInfoWithServer() throws IOException {
        String clientInfo = formatClientInfoString(languageData, key);
        sendStr(clientInfo);
        String serverResponse = receiveStr();

        if (serverResponse.contains("nsupported") || serverResponse.contains("不") || serverResponse.contains("旧")) {
            say(serverResponse);
            if (enableAutoUpdate) {
                sendStr("true");
                String versions = serverResponse.split(":")[1];
                String latestVersion = versions.split("\\|")[versions.split("\\|").length - 1];
                checkUpdate(CLIENT_FILE_PREFIX + latestVersion);
            } else {
                sendStr("false");
                hookSocket.close();
                say(languageData.PLEASE_UPDATE_MANUALLY);
                if (!isGUIMode) exitAndFreeze(2);
            }
        } else if (serverResponse.contains("exit") || serverResponse.contains("退") || serverResponse.contains("错误")
                || serverResponse.contains("denied") || serverResponse.contains("already")
                || serverResponse.contains("过期") || serverResponse.contains("占")) {
            say(serverResponse);
            if (!isGUIMode) exitAndFreeze(0);
        } else {
            lastReceivedTime = System.currentTimeMillis();
            if (OshiUtils.isWindows()) {
                int latency = TcpPingUtil.ping(remoteDomainName, hostHookPort, 1000);
                if (latency == -1 || latency > 200) {
                    loggist.say(new State(LogType.INFO, "SERVER", languageData.TOO_LONG_LATENCY_MSG));
                    loggist.say(new State(LogType.INFO, "SERVER", serverResponse));
                } else {
                    if (serverResponse.trim().equals(languageData.CONNECTION_BUILD_UP_SUCCESSFULLY.trim())) {
                        loggist.say(new State(LogType.INFO, "SERVER", serverResponse + " " + latency + "ms"));
                    } else {
                        loggist.say(new State(LogType.INFO, "SERVER", serverResponse));
                    }
                }
            } else {
                loggist.say(new State(LogType.INFO, "SERVER", serverResponse));
            }
        }
    }

    private static void promptForAccessKey() {
        if (key == null) {
            sayInfoNoNewLine(languageData.PLEASE_ENTER_ACCESS_CODE);
            key = inputScanner.nextLine();
        }
    }

    private static void promptForLocalPort() {
        if (localPort == INVALID_LOCAL_PORT) {
            sayInfoNoNewLine(languageData.ENTER_PORT_MSG);
            String input = inputScanner.nextLine();
            try {
                localPort = Integer.parseInt(input);
            } catch (Exception e) {
                say(languageData.PORT_OUT_OF_RANGE_MSG, LogType.ERROR);
                exitAndFreeze(-1);
            }
        }
    }

    public static void listenForServerCommands() throws IOException {
        String message;
        while ((message = receiveStr()) != null) {
            lastReceivedTime = System.currentTimeMillis();
            try {
                checkForInterruption();
            } catch (InterruptedException e) {
                say("隧道正在停止...");
                return;
            }
            if (message.startsWith(":>")) {
                handleServerCommand(message.substring(2));
            } else if (message.contains("This access code have") || message.contains("消耗") || message.contains("使用链接")) {
                loggist.say(new State(LogType.WARNING, "SERVER", message));
            } else {
                say(message);
            }
        }
        throw new IOException("Connection closed.");
    }

    private static void handleServerCommand(String command) {
        String[] parts = command.split(";");
        switch (parts[0]) {
            case "sendSocketTCP" -> {
                if (!isDisableTCP) ThreadManager.runAsync(() -> createNewTCPConnection(parts[1], parts[2]));
            }
            case "sendSocketUDP" -> {
                if (!isDisableUDP) ThreadManager.runAsync(() -> createNewUDPConnection(parts[1], parts[2]));
            }
            case "exitNoFlow" -> {
                say(languageData.NO_FLOW_LEFT, LogType.ERROR);
                exitAndFreeze(0);
            }
            case null, default -> remotePort = Integer.parseInt(parts[0]);
        }
    }

    private static void checkForInterruption() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException("Interrupted");
    }

    private static void handleConnectionFailure(Exception e) {
        CheckAliveThread.stopThread();
        attemptReconnection();
    }

    private static void attemptReconnection() {
        say(languageData.FAIL_TO_BUILD_A_CHANNEL_FROM + remoteDomainName, LogType.ERROR);
        if (enableAutoReconnect) {
            for (int i = 0; i < reconnectionIntervalSeconds; i++) {
                languageData.sayReconnectMsg(reconnectionIntervalSeconds - i);
                Sleeper.sleep(1000);
            }
            isReconnectedOperation = true;
            main(new String[]{"--key=" + key, "--local-port=" + localPort});
            System.exit(0);
        } else {
            exitAndFreeze(-1);
        }
    }

    public static void exitAndFreeze(int exitCode) {
        say("Press enter to exit...");
        inputScanner.nextLine();
        System.exit(exitCode);
    }

    public static final String ASCII_LOGO = """
            
               _____                                    
              / ____|                                   
             | |        ___   _ __    ___   __  __   ___
             | |       / _ \\ | '__|  / _ \\  \\ \\\\/ /  / _ \\
             | |____  |  __/ | |    | (_) |  >  <  |  __/
              \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
                                                        
                                                         \
            """;

    public static void printLogo() {
        say(ASCII_LOGO);
    }

    public static void printBasicInfo() {
        speakAnnouncement();
        say(languageData.VERSION + VersionInfo.VERSION);
        if (isDisableTCP) say(languageData.WARNING_TCP_DISABLED, LogType.WARNING);
        if (isDisableUDP) say(languageData.WARNING_UDP_DISABLED, LogType.WARNING);
    }

    private static void speakAnnouncement() {
        say(languageData.IF_YOU_SEE_EULA);
        VersionInfo.outPutEula();
    }

    public static String formatClientInfoString(LanguageData languageData, String key) {
        String versionToReport = isTestUpdate ? "0.0.1" : VersionInfo.VERSION;
        String info = languageData.getCurrentLanguage() + ";" + versionToReport + ";" + key + ";";
        if (!isDisableTCP) info = info.concat("T");
        if (!isDisableUDP) info = info.concat("U");
        return info;
    }

    public static void sayInfoNoNewLine(String str) {
        loggist.sayNoNewLine(new State(LogType.INFO, "HOST-CLIENT", str));
    }

    public static void say(String str) {
        // 增加判空保护：如果 loggist 未初始化，直接输出到控制台，防止空指针崩溃
        if (loggist != null) {
            loggist.say(new State(LogType.INFO, "HOST-CLIENT", str));
        } else {
            System.out.println("[LOG-PENDING] " + str);
        }
    }

    public static void say(String str, LogType logType) {
        if (loggist != null) {
            loggist.say(new State(logType, "HOST-CLIENT", str));
        } else {
            System.out.println("[LOG-PENDING] " + str);
        }
    }

    /**
     * [新增] 鲁棒连接逻辑：依次尝试解析出的所有 IP（包括 IPv4 和 IPv6）
     */
    private static Socket connectToLocalRobustly(String host, int port) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        IOException lastException = null;
        for (InetAddress address : addresses) {
            try {
                debugOperation("Trying local address: " + address);
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(address, port), 2000); // 2秒连接超时
                return socket;
            } catch (IOException e) {
                lastException = e;
            }
        }
        throw (lastException != null) ? lastException : new IOException("Failed to resolve " + host);
    }

    public static void createNewTCPConnection(String socketID, String remoteAddress) {
        debugOperation("Creating TCP Tunnel. ID: " + socketID);
        Socket localServerSocket = null;
        SecureSocket neoTransferSocket = null;
        try {
            if (!ProxyOperator.PROXY_IP_TO_LOCAL_SERVER.isEmpty()) {
                localServerSocket = ProxyOperator.getHandledSocket(ProxyOperator.Type.TO_LOCAL, localPort);
            } else {
                // [修改] 使用鲁棒连接逻辑适配双栈
                localServerSocket = connectToLocalRobustly(localDomainName, localPort);
            }

            if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
                neoTransferSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, hostConnectPort);
            } else {
                neoTransferSocket = new SecureSocket(remoteDomainName, hostConnectPort);
            }

            neoTransferSocket.sendStr("TCP" + ";" + socketID);

            if (showConnection) {
                say(languageData.A_TCP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.BUILD_UP);
            }

            TCPTransformer serverToNeoTask = new TCPTransformer(neoTransferSocket, localServerSocket, enableProxyProtocol);
            TCPTransformer neoToServerTask = new TCPTransformer(localServerSocket, neoTransferSocket, false);
            ThreadManager connectionThreadManager = new ThreadManager(serverToNeoTask, neoToServerTask);

            connectionThreadManager.startAsyncWithCallback(result -> {
                if (showConnection) {
                    say(languageData.A_TCP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.DESTROY);
                }
                connectionThreadManager.close();
            });

        } catch (Exception e) {
            debugOperation(e);
            if (showConnection) say(languageData.FAIL_TO_CONNECT_LOCALHOST + localPort, LogType.ERROR);
            close(localServerSocket, neoTransferSocket);
        }
    }

    public static void createNewUDPConnection(String socketID, String remoteAddress) {
        SecureSocket neoTransferSocket = null;
        DatagramSocket datagramSocket = null;
        try {
            neoTransferSocket = new SecureSocket(remoteDomainName, hostConnectPort);

            // [修改] 死锁 IPv4 栈：强制 DatagramSocket 绑定到 IPv4 的 0.0.0.0 通配符地址
            // 这样创建的 Socket 将只处理 IPv4 报文，绕过双栈环境下的 IPv6 干扰
            datagramSocket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0));

            neoTransferSocket.sendStr("UDP" + ";" + socketID);

            if (showConnection) {
                say(languageData.A_UDP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.BUILD_UP);
            }

            UDPTransformer localToNeoTask = new UDPTransformer(datagramSocket, neoTransferSocket);
            UDPTransformer neoToLocalTask = new UDPTransformer(neoTransferSocket, datagramSocket);
            ThreadManager connectionThreadManager = new ThreadManager(localToNeoTask, neoToLocalTask);

            connectionThreadManager.startAsyncWithCallback(result -> {
                if (showConnection) {
                    say(languageData.A_UDP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.DESTROY);
                }
                connectionThreadManager.close();
            });

        } catch (Exception e) {
            debugOperation(e);
            say(languageData.FAIL_TO_CONNECT_LOCALHOST + localPort, LogType.ERROR);
            close(datagramSocket, neoTransferSocket);
        }
    }

    public static File getCurrentFile() {
        try {
            String jarFilePath = NeoLink.class.getProtectionDomain().getCodeSource().getLocation().getFile();
            jarFilePath = java.net.URLDecoder.decode(jarFilePath, StandardCharsets.UTF_8);
            return new File(jarFilePath);
        } catch (Exception ignore) {
            return null;
        }
    }
}
