package neoproxy.neolink.core;

import top.ceroxe.api.print.log.LogType;
import top.ceroxe.api.print.log.Loggist;
import top.ceroxe.api.print.log.State;
import top.ceroxe.api.thread.ThreadManager;
import top.ceroxe.api.utils.TimeUtils;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.config.NodeConfig;
import neoproxy.neolink.gui.ComposeEntryKt;
import neoproxy.neolink.network.NodeFetcher;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * NeoLink desktop/application entrypoint.
 *
 * <p>This class intentionally contains only application-layer concerns:
 * command-line parsing, config state, node selection, language, logging and
 * process startup. The tunnel protocol itself is owned by NeoLinkAPI through
 * {@link NeoLinkCoreRunner}.</p>
 */
public final class NeoLink {
    public static final String CLIENT_FILE_PREFIX = "NeoLink-";
    public static final String TEST_UPDATE_VERSION = "0.0.1";
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final int INVALID_LOCAL_PORT = -1;

    public static int remotePort;
    public static String remoteDomainName = "localhost";
    public static String localDomainName = "localhost";
    public static int hostHookPort = NodeConfig.DEFAULT_HOST_HOOK_PORT;
    public static int hostConnectPort = NodeConfig.DEFAULT_HOST_CONNECT_PORT;
    public static String key = null;
    public static int localPort = INVALID_LOCAL_PORT;
    public static String proxyIPToLocalServer = "";
    public static String proxyIPToNeoServer = "";
    public static int heartbeatPacketDelay = 1000;
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
    public static boolean isNoEffectMode = false;

    private static boolean shouldAutoStartInGUI = false;
    private static boolean noColor = false;

    private NeoLink() {
    }

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
        debugOperation("Mode: " + (isGUIMode ? "GUI" : "CLI") + ", Debug: " + isDebugMode);

        if (isGUIMode) {
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

        if (!isReconnectedOperation) {
            printLogo();
            printBasicInfo();
        }

        try {
            promptForAccessKey();
            promptForLocalPort();
            NeoLinkCoreRunner.runCore(remoteDomainName, localPort, key);
        } catch (Exception e) {
            debugOperation(e);
            exitAndFreeze(-1);
        }
    }

    private static void loadNodeConfiguration() {
        debugOperation("Attempting to load configuration for node: " + specifiedNodeName);
        File nodeFile = new File(ConfigOperator.WORKING_DIR, NodeConfig.NODE_LIST_FILE_NAME);
        try {
            if (!nodeFile.exists()) {
                throw new IllegalArgumentException(NodeConfig.NODE_LIST_FILE_NAME + " file not found.");
            }
            NodeConfig node = NodeConfig.findByName(nodeFile, specifiedNodeName);
            if (node == null) {
                throw new IllegalArgumentException("Node not found.");
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
        if (languageData != null) {
            return;
        }
        Locale defaultLocale = Locale.getDefault();
        languageData = defaultLocale.getLanguage().contains("zh")
                ? LanguageData.getChineseLanguage()
                : new LanguageData();
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
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException(parts[0] + " requires a value.");
        }
        switch (parts[0]) {
            case "--key" -> key = parts[1];
            case "--local-port" -> localPort = parsePort(parts[1], "--local-port");
            case "--output-file" -> outputFilePath = parts[1];
            case "--node" -> specifiedNodeName = parts[1];
            default -> {
            }
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
            default -> {
            }
        }
    }

    public static void initializeLogger() {
        File logsDir = new File(ConfigOperator.WORKING_DIR, "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File logFile = resolveLogFile(logsDir);
        loggist = new Loggist(logFile);
        if (noColor) {
            loggist.disableColor();
        }
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
                localPort = parsePort(input, "local port");
            } catch (Exception e) {
                say(languageData.PORT_OUT_OF_RANGE_MSG, LogType.ERROR);
                exitAndFreeze(-1);
            }
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
                                                        

            """;

    public static void printLogo() {
        say(ASCII_LOGO);
    }

    public static void printBasicInfo() {
        speakAnnouncement();
        say(languageData.VERSION + getClientVersionToReport());
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

    static String getClientVersionToReport() {
        return isTestUpdate ? TEST_UPDATE_VERSION : VersionInfo.VERSION;
    }

    public static void sayInfoNoNewLine(String str) {
        if (loggist != null) {
            loggist.sayNoNewLine(new State(LogType.INFO, "HOST-CLIENT", str));
        } else {
            System.out.print("[LOG-PENDING] " + str);
        }
    }

    public static void say(String str) {
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
