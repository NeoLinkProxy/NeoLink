package neoproxy.neolink.cli;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.core.VersionInfo;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import neoproxy.neolink.util.LogSink;
import top.ceroxe.api.print.log.LogType;
import top.ceroxe.api.print.log.Loggist;
import top.ceroxe.api.print.log.State;
import top.ceroxe.api.utils.TimeUtils;

import java.io.File;
import java.util.Scanner;

/**
 * 客户端控制台 / 日志桥（client console & logger bridge）。
 *
 * <p>这里统一处理 CLI prompt、日志初始化、用户可见输出（user-facing output）与基础启动横幅。
 * 入口类不再直接关心 logger fallback、ANSI color 或版本展示细节。</p>
 */
public final class ClientConsole {
    private static final Scanner INPUT_SCANNER = new Scanner(System.in);

    private ClientConsole() {
    }

    public static void initializeLogger(boolean noColor) {
        File logsDir = new File(ConfigOperator.WORKING_DIR, "logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            throw new IllegalArgumentException("Failed to create logs directory: " + logsDir.getAbsolutePath());
        }
        File logFile = resolveLogFile(logsDir);
        Loggist loggist = new Loggist(logFile);
        if (noColor) {
            loggist.disableColor();
        }
        loggist.openWriteChannel();
        RuntimeState.setLogSink((level, tag, message) -> loggist.say(new State(toLogType(level), tag, message)));
    }

    public static void requestAccessKeyIfMissing() {
        ensureLanguageDetected();
        if (ConnectionState.snapshot().key() == null) {
            sayInfoNoNewLine(RuntimeState.languageData().PLEASE_ENTER_ACCESS_CODE);
            ConnectionState.setKey(INPUT_SCANNER.nextLine());
        }
    }

    public static void requestLocalPortIfMissing() {
        ensureLanguageDetected();
        if (ConnectionState.snapshot().localPort() == NeoLink.INVALID_LOCAL_PORT) {
            sayInfoNoNewLine(RuntimeState.languageData().ENTER_PORT_MSG);
            String input = INPUT_SCANNER.nextLine();
            try {
                ConnectionState.setLocalPort(CommandLineProcessor.parsePort(input, "local port"));
            } catch (Exception e) {
                say(RuntimeState.languageData().PORT_OUT_OF_RANGE_MSG, LogType.ERROR);
                exitAndFreeze(-1);
            }
        }
    }

    public static void exitAndFreeze(int exitCode) {
        say("按回车键退出 / Press Enter to exit...");
        INPUT_SCANNER.nextLine();
        NeoLink.requestExit(exitCode);
    }

    public static void printLogo() {
        say(NeoLink.ASCII_LOGO);
    }

    public static void printBasicInfo() {
        ensureLanguageDetected();
        LanguageData currentLanguage = RuntimeState.languageData();
        speakAnnouncement();
        say(currentLanguage.VERSION + getClientVersionToReport());
        if (FeatureState.snapshot().disableTcp()) {
            say(currentLanguage.WARNING_TCP_DISABLED, LogType.WARNING);
        }
        if (FeatureState.snapshot().disableUdp()) {
            say(currentLanguage.WARNING_UDP_DISABLED, LogType.WARNING);
        }
    }

    public static String getClientVersionToReport() {
        return FeatureState.snapshot().testUpdate() ? NeoLink.TEST_UPDATE_VERSION : VersionInfo.VERSION;
    }

    public static void sayInfoNoNewLine(String str) {
        LogSink sink = RuntimeState.logSink();
        if (sink != null) {
            sink.log(LogSink.Level.INFO, "HOST-CLIENT", str);
        } else {
            System.out.print("[LOG-PENDING] " + str);
        }
    }

    public static void say(String str) {
        LogSink sink = RuntimeState.logSink();
        if (sink != null) {
            sink.log(LogSink.Level.INFO, "HOST-CLIENT", str);
        } else {
            System.out.println("[LOG-PENDING] " + str);
        }
    }

    public static void say(String str, LogType logType) {
        LogSink sink = RuntimeState.logSink();
        if (sink != null) {
            sink.log(toLogLevel(logType), "HOST-CLIENT", str);
        } else {
            System.out.println("[LOG-PENDING] " + str);
        }
    }

    private static LogType toLogType(LogSink.Level level) {
        return switch (level) {
            case WARNING -> LogType.WARNING;
            case ERROR -> LogType.ERROR;
            default -> LogType.INFO;
        };
    }

    private static LogSink.Level toLogLevel(LogType logType) {
        return switch (logType) {
            case WARNING -> LogSink.Level.WARNING;
            case ERROR -> LogSink.Level.ERROR;
            default -> LogSink.Level.INFO;
        };
    }

    private static void speakAnnouncement() {
        say(RuntimeState.languageData().IF_YOU_SEE_EULA);
        VersionInfo.outPutEula();
    }

    private static void ensureLanguageDetected() {
        if (RuntimeState.languageData() == null) {
            LanguageManager.detectLanguage();
        }
    }

    private static File resolveLogFile(File defaultLogsDir) {
        String configuredOutputFilePath = FeatureState.snapshot().outputFilePath();
        if (configuredOutputFilePath == null || configuredOutputFilePath.isBlank()) {
            return new File(defaultLogsDir, TimeUtils.getCurrentTimeAsFileName(false) + ".log");
        }

        File logFile = new File(configuredOutputFilePath);
        if (!logFile.isAbsolute()) {
            logFile = new File(ConfigOperator.WORKING_DIR, configuredOutputFilePath);
        }
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Failed to create log directory: " + parent.getAbsolutePath());
        }
        return logFile;
    }
}
