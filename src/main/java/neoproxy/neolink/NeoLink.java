package neoproxy.neolink;

import neoproxy.neolink.gui.AppStart;
import neoproxy.neolink.gui.MainWindowController;
import neoproxy.neolink.threads.CheckAliveThread;
import neoproxy.neolink.threads.TCPTransformer;
import neoproxy.neolink.threads.UDPTransformer;
import plethora.net.NetworkUtils;
import plethora.net.SecureSocket;
import plethora.os.detect.OSDetector;
import plethora.print.log.LogType;
import plethora.print.log.Loggist;
import plethora.print.log.State;
import plethora.thread.ThreadManager;
import plethora.time.Time;
import plethora.utils.Sleeper;
import plethora.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproxy.neolink.InternetOperator.*;
import static neoproxy.neolink.UpdateManager.checkUpdate;

public class NeoLink {
    public static final String CLIENT_FILE_PREFIX = "NeoLink-";
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final int INVALID_LOCAL_PORT = -1;
    private static final File currentFile = getCurrentFile();
    public static volatile long lastReceivedTime = System.currentTimeMillis();
    public static int remotePort;
    public static String remoteDomainName = "localhost";
    public static String localDomainName = "localhost";
    public static int hostHookPort = 44801;
    public static int hostConnectPort = 44802;
    public static SecureSocket hookSocket;
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

    // 【新增】是否向后端透传 Proxy Protocol
    public static boolean enableProxyProtocol = false;

    public static int reconnectionIntervalSeconds = 30;
    public static double savedWindowX = 100;
    public static double savedWindowY = 100;
    public static double savedWindowWidth = 950;
    public static double savedWindowHeight = 700;
    public static Scanner inputScanner = new Scanner(System.in);
    public static MainWindowController mainWindowController = null;
    public static boolean isGUIMode = true;
    public static boolean isDisableUDP = false;
    public static boolean isDisableTCP = false;
    private static boolean shouldAutoStartInGUI = false;
    private static boolean isBackend = false;
    private static boolean noColor = false;

    public static void main(String[] args) {
        parseCommandLineArgs(args);
        killCmdWindowIfNeeded(args);

        if (isGUIMode) {
            AppStart.main(args, shouldAutoStartInGUI);
            System.exit(0);
        }
        initializeLogger();
        detectLanguage();
        ConfigOperator.readAndSetValue();
        ProxyOperator.init();
        if (!isReconnectedOperation) {
            printLogo();
            printBasicInfo();
        }
        try {
            promptForAccessKey();
            connectToNeoServer();
            exchangeClientInfoWithServer();

            // 启动智能心跳线程
            CheckAliveThread.startThread();

            promptForLocalPort();
            listenForServerCommands();
        } catch (Exception e) {
            handleConnectionFailure(e);
        }
    }

    private static void killCmdWindowIfNeeded(String[] args) {
        if (!isBackend && isGUIMode) {
            if (currentFile != null && currentFile.getAbsolutePath().endsWith(".exe")) {
                CopyOnWriteArrayList<String> newArgs = new CopyOnWriteArrayList<>();
                newArgs.add(currentFile.getAbsolutePath());
                newArgs.addAll(Arrays.asList(args));
                newArgs.add("--backend");
                ProcessBuilder processBuilder = new ProcessBuilder(newArgs);
                try {
                    processBuilder.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.exit(2);
            }
        }
    }

    public static void detectLanguage() {
        if (languageData == null) {
            Locale defaultLocale = Locale.getDefault();
            if (defaultLocale.getLanguage().contains("zh")) {
                languageData = LanguageData.getChineseLanguage();
                say("使用zh-ch作为备选语言");
            } else {
                languageData = new LanguageData();
            }
        }
    }

    private static void parseCommandLineArgs(String[] args) {
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
            case "--backend" -> isBackend = true;
            case "--disable-tcp" -> isDisableTCP = true;
            case "--disable-udp" -> isDisableUDP = true;
            // 【新增】支持命令行开启 Proxy Protocol
            case "--enable-pp" -> enableProxyProtocol = true;
        }
    }

    public static void initializeLogger() {
        File logFile;
        if (outputFilePath != null) {
            logFile = new File(outputFilePath);
        } else {
            String logsDirPath = CURRENT_DIR_PATH + File.separator + "logs";
            File logsDir = new File(logsDirPath);
            logsDir.mkdirs();
            logFile = new File(logsDirPath, Time.getCurrentTimeAsFileName(false) + ".log");
        }
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            Loggist logger = new Loggist(logFile);
            logger.openWriteChannel();
            loggist = logger;
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize logger", e);
        }
        if (noColor) {
            loggist.disableColor();
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
                String[] versionArray = versions.split("\\|");
                String latestVersion = versionArray[versionArray.length - 1];
                checkUpdate(CLIENT_FILE_PREFIX + latestVersion);
            } else {
                sendStr("false");
                hookSocket.close();
                say(languageData.PLEASE_UPDATE_MANUALLY);
                if (isGUIMode) {
                    mainWindowController.stopService();
                } else {
                    exitAndFreeze(2);
                }
            }
        } else if (serverResponse.contains("exit") || serverResponse.contains("退") || serverResponse.contains("错误")
                || serverResponse.contains("denied") || serverResponse.contains("already")
                || serverResponse.contains("过期") || serverResponse.contains("占")) {
            say(serverResponse);
            if (!isGUIMode) {
                exitAndFreeze(0);
            }
        } else {
            // 初始化时间戳
            lastReceivedTime = System.currentTimeMillis();
            if (OSDetector.isWindows()) {
                int latency = NetworkUtils.getLatency(remoteDomainName);
                if (latency == -1) {
                    loggist.say(new State(LogType.INFO, "SERVER", languageData.TOO_LONG_LATENCY_MSG));
                    loggist.say(new State(LogType.INFO, "SERVER", serverResponse));
                } else {
                    loggist.say(new State(LogType.INFO, "SERVER", serverResponse + " " + latency + "ms"));
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
                int port = Integer.parseInt(input);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Port out of range");
                }
                localPort = port;
            } catch (IllegalArgumentException e) {
                say(languageData.PORT_OUT_OF_RANGE_MSG, LogType.ERROR);
                exitAndFreeze(-1);
            }
        }
    }

    public static void listenForServerCommands() throws IOException {
        String message;
        while ((message = receiveStr()) != null) {
            // 【被动心跳核心】更新活跃时间
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
        throw new IOException("Connection to server was closed by remote host.");
    }

    private static void handleServerCommand(String command) {
        String[] parts = command.split(";");
        //服务端指令 sendCommand    sendSocketTCP;socketID;getInternetAddressAndPort(client))
        switch (parts[0]) {
            case "sendSocketTCP" -> {
                if (!isDisableTCP) {
                    ThreadManager.runAsync(() -> {
                        createNewTCPConnection(parts[1], parts[2]);
                    });
                }
            }
            case "sendSocketUDP" -> {
                if (!isDisableUDP) {
                    ThreadManager.runAsync(() -> {
                        createNewUDPConnection(parts[1], parts[2]);
                    });
                }
            }
            case "exitNoFlow" -> {
                say(languageData.NO_FLOW_LEFT, LogType.ERROR);
                exitAndFreeze(0);
            }
            case null, default -> remotePort = Integer.parseInt(parts[0]);
        }
    }

    private static void checkForInterruption() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("隧道被用户中断");
        }
    }

    private static void handleConnectionFailure(Exception e) {
        // 在被动心跳模式下，如果这里抛异常，说明是真的断了或者密钥错位了
        debugOperation(e);
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
        String info = languageData.getCurrentLanguage() + ";" + VersionInfo.VERSION + ";" + key + ";";
        if (!isDisableTCP) {
            info = info.concat("T");
        }
        if (!isDisableUDP) {
            info = info.concat("U");
        }
        return info;
    }

    public static void sayInfoNoNewLine(String str) {
        loggist.sayNoNewLine(new State(LogType.INFO, "HOST-CLIENT", str));
    }

    public static void debugOperation(Exception e) {
        if (isDebugMode) {
            String exceptionMsg = StringUtils.getExceptionMsg(e);
            System.out.println(exceptionMsg);
            loggist.write(exceptionMsg, true);
        }
    }

    public static void say(String str) {
        loggist.say(new State(LogType.INFO, "HOST-CLIENT", str));
    }

    public static void say(String str, LogType logType) {
        loggist.say(new State(logType, "HOST-CLIENT", str));
    }

    public static void createNewTCPConnection(String socketID, String remoteAddress) {
        Socket localServerSocket = null;
        SecureSocket neoTransferSocket = null;
        try {
            if (!ProxyOperator.PROXY_IP_TO_LOCAL_SERVER.isEmpty()) {
                localServerSocket = ProxyOperator.getHandledSocket(ProxyOperator.Type.TO_LOCAL, localPort);
            } else {
                localServerSocket = new Socket(localDomainName, localPort);
            }
            if (!ProxyOperator.PROXY_IP_TO_NEO_SERVER.isEmpty()) {
                neoTransferSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, hostConnectPort);
            } else {
                neoTransferSocket = new SecureSocket(remoteDomainName, hostConnectPort);
            }
            // 客户端发送标识
            //格式：TCP;ID   或者 UDP;ID
            neoTransferSocket.sendStr("TCP" + ";" + socketID);

            if (showConnection) {
                say(languageData.A_TCP_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.BUILD_UP);
            }

            // 【核心修改】传入 enableProxyProtocol 参数
            // 方向：Server -> Local (需要处理 Proxy Protocol)
            TCPTransformer serverToNeoTask = new TCPTransformer(neoTransferSocket, localServerSocket, enableProxyProtocol);

            // 方向：Local -> Server (不需要处理)
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
            if (showConnection) {
                say(languageData.FAIL_TO_CONNECT_LOCALHOST + localPort, LogType.ERROR);
            }
            close(localServerSocket, neoTransferSocket);
        }
    }

    public static void createNewUDPConnection(String socketID, String remoteAddress) {
        SecureSocket neoTransferSocket = null;
        DatagramSocket datagramSocket = null;
        try {
            neoTransferSocket = new SecureSocket(remoteDomainName, hostConnectPort);
            datagramSocket = new DatagramSocket();
            // 客户端发送标识
            //格式：TCP;ID   或者 UDP;ID
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