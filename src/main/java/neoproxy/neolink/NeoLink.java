package neoproxy.neolink;

import fun.ceroxe.api.OshiUtils;
import fun.ceroxe.api.net.SecureSocket;
import fun.ceroxe.api.net.TcpPingUtil;
import fun.ceroxe.api.print.log.LogType;
import fun.ceroxe.api.print.log.Loggist;
import fun.ceroxe.api.print.log.State;
import fun.ceroxe.api.thread.ThreadManager;
import fun.ceroxe.api.utils.Sleeper;
import fun.ceroxe.api.utils.TimeUtils;
import neoproxy.neolink.gui.ComposeEntryKt;
import neoproxy.neolink.threads.CheckAliveThread;
import neoproxy.neolink.threads.TCPTransformer;
import neoproxy.neolink.threads.UDPTransformer;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproxy.neolink.Debugger.debugOperation;
import static neoproxy.neolink.InternetOperator.*;
import static neoproxy.neolink.UpdateManager.checkUpdate;

public class NeoLink {
    public static final String CLIENT_FILE_PREFIX = "NeoLink-";
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final int INVALID_LOCAL_PORT = -1;
    public static volatile long lastReceivedTime = System.currentTimeMillis();
    public static int remotePort;
    public static String remoteDomainName = "localhost";
    public static String localDomainName = "localhost";
    public static int hostHookPort = 44801;
    public static int hostConnectPort = 44802;
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

    public static Scanner inputScanner = new Scanner(System.in);
    public static boolean isGUIMode = true;
    public static boolean isDisableUDP = false;
    public static boolean isDisableTCP = false;
    // New variable for storing the node name from command line
    public static String specifiedNodeName = null;
    // [新增] 测试更新标志
    public static boolean isTestUpdate = false;
    private static boolean shouldAutoStartInGUI = false;
    private static boolean noColor = false;

    // [新增] 供 Kotlin UI 调用以检查是否需要自动启动
    public static boolean shouldAutoStart() {
        return shouldAutoStartInGUI;
    }

    public static void main(String[] args) {
        ConfigOperator.initEnvironment();
        // [DEBUG] Entry point
        parseCommandLineArgs(args);
        debugOperation("Entering main() method.");
        debugOperation("Command line arguments parsed. Mode: " + (isGUIMode ? "GUI" : "CLI") + ", Debug: " + isDebugMode);

        if (isGUIMode) {
            debugOperation("GUI Mode detected. Delegating to ComposeEntryKt.main().");
            // [修改] 调用 Kotlin Compose 的 Main 方法
            ComposeEntryKt.main(args);
            // Compose Desktop 应用关闭后退出 JVM
            System.exit(0);
        }

        // --- 以下为 CLI 模式逻辑 ---

        debugOperation("CLI Mode proceeding. Initializing logger.");
        initializeLogger();

        debugOperation("Detecting language...");
        detectLanguage();

        debugOperation("Reading configuration values...");
        ConfigOperator.readAndSetValue();

        // [New Logic] Load Node Config if specified
        if (specifiedNodeName != null) {
            loadNodeConfiguration();
        }

        debugOperation("Initializing ProxyOperator...");
        ProxyOperator.init();

        if (!isReconnectedOperation) {
            debugOperation("Not a reconnection operation. Printing logo and info.");
            printLogo();
            printBasicInfo();
        } else {
            debugOperation("This is a reconnection operation.");
        }

        try {
            debugOperation("Prompting for access key (if null).");
            promptForAccessKey();

            debugOperation("Attempting to connect to NeoServer at " + remoteDomainName + ":" + hostHookPort);
            connectToNeoServer();
            debugOperation("Connection established. Exchanging client info.");

            exchangeClientInfoWithServer();

            debugOperation("Starting CheckAliveThread.");
            CheckAliveThread.startThread();

            debugOperation("Prompting for local port (if invalid).");
            promptForLocalPort();

            debugOperation("Entering main loop: listening for server commands.");
            listenForServerCommands();
        } catch (Exception e) {
            debugOperation("Exception occurred in main execution flow.");
            debugOperation(e);
            handleConnectionFailure(e);
        }
    }

    /**
     * Tries to load node configuration from node.json based on specifiedNodeName.
     * Falls back to existing configuration (GUI mode defaults) if any error occurs.
     */
    private static void loadNodeConfiguration() {
        debugOperation("Attempting to load configuration for node: " + specifiedNodeName);
        File nodeFile = new File(ConfigOperator.WORKING_DIR, "node.json");

        try {
            if (!nodeFile.exists()) {
                throw new IOException("node.json file not found in current directory.");
            }

            String jsonContent = new String(Files.readAllBytes(nodeFile.toPath()), StandardCharsets.UTF_8);

            // Manual JSON parsing to avoid adding external dependencies.
            // Looking for the object containing "name": "specifiedNodeName"
            // We split by closing braces to roughly separate objects, assuming a flat array structure.
            String[] rawObjects = jsonContent.split("}");
            boolean nodeFound = false;

            for (String obj : rawObjects) {
                // Check if this object contains the name we are looking for
                // Regex matches: "name"\s*:\s*"specifiedNodeName"
                Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"" + Pattern.quote(specifiedNodeName) + "\"");
                Matcher nameMatcher = namePattern.matcher(obj);

                if (nameMatcher.find()) {
                    // Node found, extract other fields
                    nodeFound = true;
                    debugOperation("Node '" + specifiedNodeName + "' found in json. Parsing details...");

                    // Extract Address
                    Pattern addressPattern = Pattern.compile("\"address\"\\s*:\\s*\"(.*?)\"");
                    Matcher addressMatcher = addressPattern.matcher(obj);
                    if (addressMatcher.find()) {
                        remoteDomainName = addressMatcher.group(1);
                    }

                    // Extract Hook Port
                    Pattern hookPortPattern = Pattern.compile("\"HOST_HOOK_PORT\"\\s*:\\s*(\\d+)");
                    Matcher hookPortMatcher = hookPortPattern.matcher(obj);
                    // 兼容旧版 json key
                    if (!hookPortMatcher.find()) {
                        hookPortPattern = Pattern.compile("\"hookPort\"\\s*:\\s*(\\d+)");
                        hookPortMatcher = hookPortPattern.matcher(obj);
                    }

                    if (hookPortMatcher.find()) {
                        hostHookPort = Integer.parseInt(hookPortMatcher.group(1));
                    }

                    // Extract Connect Port
                    Pattern connectPortPattern = Pattern.compile("\"HOST_CONNECT_PORT\"\\s*:\\s*(\\d+)");
                    Matcher connectPortMatcher = connectPortPattern.matcher(obj);
                    if (!connectPortMatcher.find()) {
                        connectPortPattern = Pattern.compile("\"connectPort\"\\s*:\\s*(\\d+)");
                        connectPortMatcher = connectPortPattern.matcher(obj);
                    }

                    if (connectPortMatcher.find()) {
                        hostConnectPort = Integer.parseInt(connectPortMatcher.group(1));
                    }

                    debugOperation("Node config applied. Address: " + remoteDomainName +
                            ", HookPort: " + hostHookPort + ", ConnectPort: " + hostConnectPort);
                    break;
                }
            }

            if (!nodeFound) {
                throw new IOException("Node with name '" + specifiedNodeName + "' not found in node.json.");
            }

        } catch (Exception e) {
            // Fallback logic: Log the error in pure English and proceed with previous values (GUI/Default mode)
            debugOperation("Failed to load node config, falling back to GUI mode. Reason: " + e.getMessage());
            if (isDebugMode) {
                e.printStackTrace();
            }
        }
    }

    public static void detectLanguage() {
        if (languageData == null) {
            Locale defaultLocale = Locale.getDefault();
            debugOperation("Detecting language. System default: " + defaultLocale.getLanguage());
            if (defaultLocale.getLanguage().contains("zh")) {
                languageData = LanguageData.getChineseLanguage();
                say("使用zh-ch作为备选语言");
                debugOperation("Language set to Chinese.");
            } else {
                languageData = new LanguageData();
                debugOperation("Language set to English/Default.");
            }
        }
    }

    private static void parseCommandLineArgs(String[] args) {
        // Can't log here easily as Debugger relies on NeoLink.isDebugMode which is set here.
        boolean hasKey = false;
        boolean hasLocalPort = false;

        for (String arg : args) {
            if (arg.contains("=")) {
                parseKeyValueArgument(arg);
                if (arg.startsWith("--key=")) {
                    hasKey = true;
                } else if (arg.startsWith("--local-port=")) {
                    hasLocalPort = true;
                }
            } else {
                parseFlagArgument(arg);
            }
        }
        if (hasKey && hasLocalPort && isGUIMode) {
            shouldAutoStartInGUI = true;
        }
    }

    private static void parseKeyValueArgument(String arg) {
        String[] parts = arg.split("=", 2);
        switch (parts[0]) {
            case "--key" -> key = parts[1];
            case "--local-port" -> localPort = Integer.parseInt(parts[1]);
            case "--output-file" -> outputFilePath = parts[1];
            case "--node" -> specifiedNodeName = parts[1]; // New argument handler
        }
    }

    private static void parseFlagArgument(String arg) {
        switch (arg) {
            case "--en-us" -> languageData = new LanguageData();
            case "--zh-cn" -> languageData = LanguageData.getChineseLanguage();
            case "--no-color" -> noColor = true;
            case "--debug" -> isDebugMode = true; // Flag enabled here
            case "--no-show-conn" -> showConnection = false;
            case "--gui" -> isGUIMode = true;
            case "--nogui" -> isGUIMode = false;
            case "--disable-tcp" -> isDisableTCP = true;
            case "--disable-udp" -> isDisableUDP = true;
            case "--enable-pp" -> enableProxyProtocol = true;
            // [新增] 解析测试更新参数
            case "--test-update" -> isTestUpdate = true;
        }
    }

    public static void initializeLogger() {
        File logsDir = new File(ConfigOperator.WORKING_DIR, "logs");
        if (!logsDir.exists()) logsDir.mkdirs();

        // 所有的日志输出都挂载在 WORKING_DIR/logs 下
        File logFile = new File(logsDir, TimeUtils.getCurrentTimeAsFileName(false) + ".log");
        loggist = new Loggist(logFile);
        if (noColor) {
            loggist.disableColor();
        }
        loggist.openWriteChannel();
    }

    private static void connectToNeoServer() throws IOException {
        debugOperation("Connecting to NeoServer (CLI context)...");
        say(languageData.CONNECT_TO + remoteDomainName + languageData.OMITTED);

        if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
            debugOperation("Using Proxy connection to NeoServer.");
            hookSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, hostHookPort);
        } else {
            debugOperation("Direct connection to " + remoteDomainName + ":" + hostHookPort);
            hookSocket = new SecureSocket(remoteDomainName, hostHookPort);
        }
        debugOperation("Socket connected successfully.");
    }

    public static void exchangeClientInfoWithServer() throws IOException {
        debugOperation("Preparing client info string...");
        String clientInfo = formatClientInfoString(languageData, key);
        debugOperation("Sending client info: " + clientInfo);
        sendStr(clientInfo);

        debugOperation("Waiting for server response...");
        String serverResponse = receiveStr();
        debugOperation("Received server response: " + serverResponse);

        if (serverResponse.contains("nsupported") || serverResponse.contains("不") || serverResponse.contains("旧")) {
            say(serverResponse);
            if (enableAutoUpdate) {
                debugOperation("Auto-update is enabled. Sending 'true' to server.");
                sendStr("true");
                String versions = serverResponse.split(":")[1];
                String[] versionArray = versions.split("\\|");
                String latestVersion = versionArray[versionArray.length - 1];
                debugOperation("Checking update for version: " + latestVersion);
                checkUpdate(CLIENT_FILE_PREFIX + latestVersion);
            } else {
                debugOperation("Auto-update disabled. Sending 'false' and exiting.");
                sendStr("false");
                hookSocket.close();
                say(languageData.PLEASE_UPDATE_MANUALLY);
                // [修改] 如果是 CLI 模式，退出。GUI 模式由 CoreRunner 捕获处理或外部 System.exit
                if (!isGUIMode) {
                    exitAndFreeze(2);
                }
            }
        } else if (serverResponse.contains("exit") || serverResponse.contains("退") || serverResponse.contains("错误")
                || serverResponse.contains("denied") || serverResponse.contains("already")
                || serverResponse.contains("过期") || serverResponse.contains("占")) {
            debugOperation("Server denied connection. Message: " + serverResponse);
            say(serverResponse);
            if (!isGUIMode) {
                exitAndFreeze(0);
            }
        } else {
            lastReceivedTime = System.currentTimeMillis();
            debugOperation("Handshake successful.");
            if (OshiUtils.isWindows()) {
                debugOperation("Calculating latency...");
                int latency = TcpPingUtil.ping(remoteDomainName, hostHookPort, 1000);
                if (latency == -1 || latency > 200) {
                    loggist.say(new State(LogType.INFO, "SERVER", languageData.TOO_LONG_LATENCY_MSG));
                    loggist.say(new State(LogType.INFO, "SERVER", serverResponse));
                } else {
                    // [修复] 只有当消息是标准的连接成功提示时，才显示延迟信息
                    // 如果是自定义消息 (CBM)，则原样显示，不带毫秒数
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
            debugOperation("Key is null, prompting user input via Scanner.");
            sayInfoNoNewLine(languageData.PLEASE_ENTER_ACCESS_CODE);
            key = inputScanner.nextLine();
            debugOperation("Key received from input.");
        } else {
            debugOperation("Key already provided via args.");
        }
    }

    private static void promptForLocalPort() {
        if (localPort == INVALID_LOCAL_PORT) {
            debugOperation("Local port invalid (-1), prompting user input.");
            sayInfoNoNewLine(languageData.ENTER_PORT_MSG);
            String input = inputScanner.nextLine();
            try {
                int port = Integer.parseInt(input);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Port out of range");
                }
                localPort = port;
                debugOperation("Local port set to: " + localPort);
            } catch (IllegalArgumentException e) {
                debugOperation("Invalid port input: " + input);
                say(languageData.PORT_OUT_OF_RANGE_MSG, LogType.ERROR);
                exitAndFreeze(-1);
            }
        } else {
            debugOperation("Local port pre-set: " + localPort);
        }
    }

    public static void listenForServerCommands() throws IOException {
        debugOperation("Entering command listening loop.");
        String message;
        while ((message = receiveStr()) != null) {
            lastReceivedTime = System.currentTimeMillis();
            debugOperation("Received command raw: " + message);

            try {
                checkForInterruption();
            } catch (InterruptedException e) {
                debugOperation("Thread interrupted during loop.");
                say("隧道正在停止...");
                return;
            }
            if (message.startsWith(":>")) {
                debugOperation("Handling standard server command.");
                handleServerCommand(message.substring(2));
            } else if (message.contains("This access code have") || message.contains("消耗") || message.contains("使用链接")) {
                debugOperation("Received warning/quota message.");
                loggist.say(new State(LogType.WARNING, "SERVER", message));
            } else {
                debugOperation("Received generic server message.");
                say(message);
            }
        }
        debugOperation("Loop exited because receiveStr() returned null (connection closed).");
        throw new IOException("Connection to server was closed by remote host.");
    }

    private static void handleServerCommand(String command) {
        debugOperation("Parsing command content: " + command);
        String[] parts = command.split(";");
        switch (parts[0]) {
            case "sendSocketTCP" -> {
                debugOperation("Command: sendSocketTCP. DisableTCP? " + isDisableTCP);
                if (!isDisableTCP) {
                    ThreadManager.runAsync(() -> {
                        debugOperation("Async: Creating new TCP connection.");
                        createNewTCPConnection(parts[1], parts[2]);
                    });
                } else {
                    debugOperation("TCP Disabled, ignoring command.");
                }
            }
            case "sendSocketUDP" -> {
                debugOperation("Command: sendSocketUDP. DisableUDP? " + isDisableUDP);
                if (!isDisableUDP) {
                    ThreadManager.runAsync(() -> {
                        debugOperation("Async: Creating new UDP connection.");
                        createNewUDPConnection(parts[1], parts[2]);
                    });
                } else {
                    debugOperation("UDP Disabled, ignoring command.");
                }
            }
            case "exitNoFlow" -> {
                debugOperation("Command: exitNoFlow. Exiting.");
                say(languageData.NO_FLOW_LEFT, LogType.ERROR);
                exitAndFreeze(0);
            }
            case null, default -> {
                debugOperation("Command default case. Updating remote port to: " + parts[0]);
                remotePort = Integer.parseInt(parts[0]);
            }
        }
    }

    private static void checkForInterruption() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("隧道被用户中断");
        }
    }

    private static void handleConnectionFailure(Exception e) {
        debugOperation("Handling connection failure.");
        debugOperation(e);
        CheckAliveThread.stopThread();
        attemptReconnection();
    }

    private static void attemptReconnection() {
        debugOperation("Attempting reconnection. Auto-reconnect enabled: " + enableAutoReconnect);
        say(languageData.FAIL_TO_BUILD_A_CHANNEL_FROM + remoteDomainName, LogType.ERROR);
        if (enableAutoReconnect) {
            for (int i = 0; i < reconnectionIntervalSeconds; i++) {
                languageData.sayReconnectMsg(reconnectionIntervalSeconds - i);
                Sleeper.sleep(1000);
            }
            debugOperation("Re-launching main() for reconnection.");
            isReconnectedOperation = true;
            main(new String[]{"--key=" + key, "--local-port=" + localPort});
            System.exit(0);
        } else {
            debugOperation("Auto-reconnect disabled. Freezing.");
            exitAndFreeze(-1);
        }
    }

    public static void exitAndFreeze(int exitCode) {
        debugOperation("Freezing program with exit code: " + exitCode);
        say("Press enter to exit the program...");
        inputScanner.nextLine();
        System.exit(exitCode);
    }

    public static void printLogo() {
        say("""
                
                   _____                                    \s
                  / ____|                                   \s
                 | |        ___   _ __    ___   __  __   ___\s
                 | |       / _ \\ | '__|  / _ \\  \\ \\/ /  / _ \\
                 | |____  |  __/ | |    | (_) |  >  <  |  __/
                  \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
                                                            \s
                                                             \
                """);
    }

    public static void printBasicInfo() {
        speakAnnouncement();
        say(languageData.VERSION + VersionInfo.VERSION);
        if (isDisableTCP) {
            say(languageData.WARNING_TCP_DISABLED, LogType.WARNING);
        }
        if (isDisableUDP) {
            say(languageData.WARNING_UDP_DISABLED, LogType.WARNING);
        }
    }

    private static void speakAnnouncement() {
        say(languageData.IF_YOU_SEE_EULA);
        VersionInfo.outPutEula();
    }

    public static String formatClientInfoString(LanguageData languageData, String key) {
        // [修改] 如果处于测试更新模式，则上报一个极低的版本号
        String versionToReport = isTestUpdate ? "0.0.1" : VersionInfo.VERSION;

        String info = languageData.getCurrentLanguage() + ";" + versionToReport + ";" + key + ";";
        if (!isDisableTCP) {
            info = info.concat("T");
        }
        if (!isDisableUDP) {
            info = info.concat("U");
        }
        debugOperation("Formatted Client Info: " + info);
        return info;
    }

    public static void sayInfoNoNewLine(String str) {
        loggist.sayNoNewLine(new State(LogType.INFO, "HOST-CLIENT", str));
    }

    public static void say(String str) {
        loggist.say(new State(LogType.INFO, "HOST-CLIENT", str));
    }

    public static void say(String str, LogType logType) {
        loggist.say(new State(logType, "HOST-CLIENT", str));
    }

    public static void createNewTCPConnection(String socketID, String remoteAddress) {
        debugOperation("Creating TCP Tunnel. ID: " + socketID + ", Remote: " + remoteAddress);
        Socket localServerSocket = null;
        SecureSocket neoTransferSocket = null;
        try {
            if (!ProxyOperator.PROXY_IP_TO_LOCAL_SERVER.isEmpty()) {
                debugOperation("Using Proxy for local server connection.");
                localServerSocket = ProxyOperator.getHandledSocket(ProxyOperator.Type.TO_LOCAL, localPort);
            } else {
                debugOperation("Connecting to local service: " + localDomainName + ":" + localPort);
                localServerSocket = new Socket(localDomainName, localPort);
            }

            if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
                debugOperation("Using Proxy for NeoServer data transfer connection.");
                neoTransferSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, hostConnectPort);
            } else {
                debugOperation("Connecting to NeoServer for data transfer: " + remoteDomainName + ":" + hostConnectPort);
                neoTransferSocket = new SecureSocket(remoteDomainName, hostConnectPort);
            }

            debugOperation("Sending TCP handshake with ID.");
            neoTransferSocket.sendStr("TCP" + ";" + socketID);

            if (showConnection) {
                say(languageData.A_TCP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.BUILD_UP);
            }

            debugOperation("Starting TCP Transformers.");
            TCPTransformer serverToNeoTask = new TCPTransformer(neoTransferSocket, localServerSocket, enableProxyProtocol);
            TCPTransformer neoToServerTask = new TCPTransformer(localServerSocket, neoTransferSocket, false);

            ThreadManager connectionThreadManager = new ThreadManager(serverToNeoTask, neoToServerTask);

            connectionThreadManager.startAsyncWithCallback(result -> {
                if (showConnection) {
                    say(languageData.A_TCP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.DESTROY);
                }
                debugOperation("TCP Connection " + socketID + " closed.");
                connectionThreadManager.close();
            });

        } catch (Exception e) {
            debugOperation("Failed to create TCP connection.");
            debugOperation(e);
            if (showConnection) {
                say(languageData.FAIL_TO_CONNECT_LOCALHOST + localPort, LogType.ERROR);
            }
            close(localServerSocket, neoTransferSocket);
        }
    }

    public static void createNewUDPConnection(String socketID, String remoteAddress) {
        debugOperation("Creating UDP Tunnel. ID: " + socketID + ", Remote: " + remoteAddress);
        SecureSocket neoTransferSocket = null;
        DatagramSocket datagramSocket = null;
        try {
            debugOperation("Connecting to NeoServer for UDP transfer.");
            neoTransferSocket = new SecureSocket(remoteDomainName, hostConnectPort);
            debugOperation("Creating local DatagramSocket.");
            datagramSocket = new DatagramSocket();

            debugOperation("Sending UDP handshake with ID.");
            neoTransferSocket.sendStr("UDP" + ";" + socketID);

            if (showConnection) {
                say(languageData.A_UDP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.BUILD_UP);
            }

            debugOperation("Starting UDP Transformers.");
            UDPTransformer localToNeoTask = new UDPTransformer(datagramSocket, neoTransferSocket);
            UDPTransformer neoToLocalTask = new UDPTransformer(neoTransferSocket, datagramSocket);
            ThreadManager connectionThreadManager = new ThreadManager(localToNeoTask, neoToLocalTask);

            connectionThreadManager.startAsyncWithCallback(result -> {
                if (showConnection) {
                    say(languageData.A_UDP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.DESTROY);
                }
                debugOperation("UDP Connection " + socketID + " closed.");
                connectionThreadManager.close();
            });

        } catch (Exception e) {
            debugOperation("Failed to create UDP connection.");
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