package neoproject.neolink;

import neoproject.neolink.gui.AppStart;
import neoproject.neolink.threads.CheckAliveThread;
import neoproject.neolink.threads.Transformer;
import plethora.net.NetworkUtils;
import plethora.net.SecureSocket;
import plethora.os.detect.OSDetector;
import plethora.os.windowsSystem.WindowsOperation;
import plethora.print.log.LogType;
import plethora.print.log.Loggist;
import plethora.print.log.State;
import plethora.thread.ThreadManager;
import plethora.time.Time;
import plethora.utils.Sleeper;
import plethora.utils.StringUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;
import java.util.Scanner;

import static neoproject.neolink.InternetOperator.*;

/**
 * NeoLink 客户端主类。
 * 负责处理命令行参数、初始化日志、与服务器建立连接、处理内网穿透逻辑以及自动重连。
 */
public class NeoLink {
    // ==================== 常量定义 (遵循 SCREAMING_SNAKE_CASE) ====================
    public static final String CLIENT_FILE_PREFIX = "NeoLink-";
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final int DEFAULT_HOST_HOOK_PORT = 801;
    public static final int DEFAULT_HOST_CONNECT_PORT = 802;
    public static final int DEFAULT_RECONNECTION_INTERVAL_SECONDS = 30;
    public static final int INVALID_LOCAL_PORT = -1;

    // ==================== 可配置的全局状态 (重命名以提高可读性) ====================
    public static int remotePort;
    public static String remoteDomainName = "localhost";
    public static String localDomainName = "localhost";
    public static int hostHookPort = DEFAULT_HOST_HOOK_PORT;
    public static int hostConnectPort = DEFAULT_HOST_CONNECT_PORT;
    public static SecureSocket hookSocket;
    public static String key = null;
    public static int localPort = INVALID_LOCAL_PORT;
    public static Loggist loggist;
    public static String outputFilePath = null;
    public static LanguageData languageData = new LanguageData();
    public static boolean isReconnectedOperation = false;
    public static boolean isDebugMode = false;
    private static boolean isGUIMode = true;
    private static boolean shouldAutoStartInGUI = false; // 新增标志位
    public static boolean enableAutoReconnect = true;
    public static int reconnectionIntervalSeconds = DEFAULT_RECONNECTION_INTERVAL_SECONDS;
    public static double savedWindowX = 100;
    public static double savedWindowY = 100;
    public static double savedWindowWidth = 950;
    public static double savedWindowHeight = 700;
    public static Scanner inputScanner = new Scanner(System.in);
    ;

    // ==================== 主流程 ====================
    public static void main(String[] args) {
        parseCommandLineArgs(args);
        if (isGUIMode) {
            // 将自动启动标志传递给 GUI
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
            // 通过验证，开始核心服务
            CheckAliveThread.startThread();
            promptForLocalPort();
            listenForServerCommands();
        } catch (Exception e) {
            handleConnectionFailure(e);
        }
    }

    /**
     * 根据系统默认语言环境自动检测并设置为中文（如果适用）。
     * 此逻辑在命令行参数解析之后、日志初始化之前执行。
     * 如果用户通过命令行指定了语言（如 --zh-ch），则此方法不会覆盖它。
     */
    public static void detectLanguage() {
        // 只有在 languageData 仍是默认的英文实例时才进行自动检测
        if ("en".equals(languageData.getCurrentLanguage())) {
            Locale defaultLocale = Locale.getDefault();
            if (defaultLocale.getLanguage().contains("zh")) {
                languageData = LanguageData.getChineseLanguage();
                // 现在 loggist 已经初始化，可以安全地打印日志
                say("使用zh-ch作为备选语言");
            }
        }
    }

    // ==================== 命令行与初始化 ====================
    private static void parseCommandLineArgs(String[] args) {
        boolean hasKey = false;
        boolean hasLocalPort = false;

        for (String arg : args) {
            if (arg.contains("=")) {
                parseKeyValueArgument(arg);

                if (arg.startsWith("--key=")) {//判断是否有这两个参数存在
                    hasKey = true;
                } else if (arg.startsWith("--local-port=")) {
                    hasLocalPort = true;
                }

            } else {
                parseFlagArgument(arg);
            }
        }
        // 如果同时存在 --key 和 --local-port 且是 GUI 模式，则设置自动启动标志
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
            case "--zh-ch" -> languageData = LanguageData.getChineseLanguage();
            case "--no-color" -> loggist.disableColor();
            case "--debug" -> isDebugMode = true;
            case "--gui" -> isGUIMode = true;
            case "--nogui" -> isGUIMode = false;
        }
    }

    public static void initializeLogger() {
        File logFile;
        if (outputFilePath != null) {
            logFile = new File(outputFilePath);
        } else {
            String logsDirPath = CURRENT_DIR_PATH + File.separator + "logs";
            File logsDir = new File(logsDirPath);
            logsDir.mkdirs(); // 确保日志目录存在
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
    }

    // ==================== 服务器交互 ====================
    private static void connectToNeoServer() throws IOException {
        say(languageData.CONNECT_TO + remoteDomainName + languageData.OMITTED);
        if (ProxyOperator.PROXY_IP_TO_NEO_SERVER != null) {
            hookSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, hostHookPort);
        } else {
            hookSocket = new SecureSocket(remoteDomainName, hostHookPort);
        }
    }

    private static void exchangeClientInfoWithServer() throws IOException {
        String clientInfo = formatClientInfoString(languageData, key);
        sendStr(clientInfo);
        String serverResponse = receiveStr();
        if (serverResponse.contains("nsupported") || serverResponse.contains("不") || serverResponse.contains("旧")) {
            String versions = serverResponse.split(":")[1];
            String[] versionArray = versions.split("\\|");
            String latestVersion = versionArray[versionArray.length - 1];
            checkUpdate(CLIENT_FILE_PREFIX + latestVersion);
        } else if (serverResponse.contains("exit") || serverResponse.contains("退") || serverResponse.contains("错误")
                || serverResponse.contains("denied") || serverResponse.contains("already")
                || serverResponse.contains("过期") || serverResponse.contains("占")) {
            say(serverResponse);
            exitAndFreeze(0);
        } else {
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

    // ==================== 用户交互 (修复版) ====================
    private static void promptForAccessKey() {
        if (key == null) {
            sayInfoNoNewLine(languageData.PLEASE_ENTER_ACCESS_CODE);
            // 使用全局 Scanner
            key = inputScanner.nextLine();
        }
    }

    private static void promptForLocalPort() {
        if (localPort == INVALID_LOCAL_PORT) {
            sayInfoNoNewLine(languageData.ENTER_PORT_MSG);
            // 使用全局 Scanner
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

    // ==================== 核心服务循环 ====================
    public static void listenForServerCommands() throws IOException {
        String message;
        while ((message = receiveStr()) != null) {
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
        // 如果循环结束，说明 receiveStr() 返回了 null，即连接已断开。
        // 我们需要主动抛出一个异常来触发重连逻辑。
        throw new IOException("Connection to server was closed by remote host.");
    }

    private static void handleServerCommand(String command) {
        String[] parts = command.split(";", 2);
        if ("sendSocket".equals(parts[0])) {
            new Thread(() -> createNewConnection(parts[1])).start();
        } else if ("exitNoFlow".equals(parts[0])) {
            say(languageData.NO_FLOW_LEFT, LogType.ERROR);
            exitAndFreeze(0);
        } else {
            remotePort = Integer.parseInt(parts[0]);
        }
    }

    private static void checkForInterruption() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("隧道被用户中断");
        }
    }

    // ==================== 错误处理与退出 (修复版) ====================
    private static void handleConnectionFailure(Exception e) {
        debugOperation(e);
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
        // 使用全局 Scanner
        inputScanner.nextLine();
        System.exit(exitCode);
    }

    // ==================== 辅助与工具方法 ====================
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
    }

    private static void speakAnnouncement() {
        say(languageData.IF_YOU_SEE_EULA);
        VersionInfo.outPutEula();
    }

    public static String formatClientInfoString(LanguageData languageData, String key) {
        return languageData.getCurrentLanguage() + ";" + VersionInfo.VERSION + ";" + key;
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

    // ==================== 核心功能: 更新检查 ====================

    /**
     * 检查并下载服务器提供的新版本客户端。
     *
     * @param fileName 新客户端文件的基础名称。
     */
    public static void checkUpdate(String fileName) {
        try {
            boolean isWindows = OSDetector.isWindows();
            // 告知服务端客户端类型 (exe 或 jar)
            sendStr(isWindows ? "exe" : "jar");
            boolean canDownload = Boolean.parseBoolean(receiveStr());
            if (!canDownload) {
                exitAndFreeze(-1);
                return;
            }
            // 确定新客户端文件的完整路径
            String fileExtension = isWindows ? ".exe" : ".jar";
            File clientFile = new File(CURRENT_DIR_PATH, fileName + fileExtension);
            // 如果文件已存在，重命名为 " - copy" 版本
            if (clientFile.exists()) {
                File backupFile = new File(clientFile.getParent(), fileName + " - copy" + fileExtension);
                clientFile.renameTo(backupFile);
                clientFile.createNewFile();
            } else {
                clientFile.createNewFile();
            }
            say(languageData.START_TO_DOWNLOAD_UPDATE);
            byte[] newClientData = receiveBytes();
            // 使用 try-with-resources 确保流被正确关闭
            try (BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(clientFile))) {
                fileOutputStream.write(newClientData);
            }
            say(languageData.DOWNLOAD_SUCCESS);
            if (isWindows) {
                // 在 Windows 上自动启动新版本
                StringBuilder command = new StringBuilder("cmd.exe /c start \"\" \"");
                command.append(clientFile.getAbsolutePath()).append("\"");
                if (key != null) {
                    command.append(" --key=").append(key);
                }
                if (localPort != INVALID_LOCAL_PORT) {
                    command.append(" --local-port=").append(localPort);
                }
                if (!isGUIMode) {
                    command.append(" --nogui");
                }
                WindowsOperation.run(command.toString());
                System.exit(0);
            } else {
                // 在非 Windows 系统上提示用户手动运行
                say(languageData.PLEASE_RUN + clientFile.getAbsolutePath());
            }
            exitAndFreeze(0);
        } catch (IOException e) {
            debugOperation(e);
            say("Fail to check updates.", LogType.ERROR);
            exitAndFreeze(0);
        }
    }

    // ==================== 网络连接管理 ====================
    public static void createNewConnection(String remoteAddress) {
        try {
            Socket localServerSocket;
            if (ProxyOperator.PROXY_IP_TO_LOCAL_SERVER != null) {
                localServerSocket = ProxyOperator.getHandledSocket(ProxyOperator.Type.TO_LOCAL, localPort);
            } else {
                localServerSocket = new Socket(localDomainName, localPort);
            }
            SecureSocket neoTransferSocket;
            if (ProxyOperator.PROXY_IP_TO_NEO_SERVER != null) {
                neoTransferSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, hostConnectPort);
            } else {
                neoTransferSocket = new SecureSocket(remoteDomainName, hostConnectPort);
            }
            neoTransferSocket.sendInt(remotePort);
            say(languageData.A_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.BUILD_UP);
            Transformer serverToNeoThread = new Transformer(localServerSocket, neoTransferSocket);
            Transformer neoToServerThread = new Transformer(neoTransferSocket, localServerSocket);
            ThreadManager threadManager = new ThreadManager(serverToNeoThread, neoToServerThread);
            threadManager.start();
            closeSocket(localServerSocket);
            closeSocket(neoTransferSocket);
            say(languageData.A_CONNECTION + remoteAddress + " -> " + localDomainName + ":" + localPort + languageData.DESTROY);
        } catch (Exception e) {
            debugOperation(e);
            say(languageData.FAIL_TO_CONNECT_LOCALHOST + localPort, LogType.ERROR);
            System.gc();
        }
    }
}