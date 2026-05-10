package neoproxy.neolink.cli;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.core.VersionInfo;
import neoproxy.neolink.platform.DesktopLogManager;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import neoproxy.neolink.util.LogSink;
import top.ceroxe.api.print.log.LogType;

import java.util.Scanner;

/**
 * 客户端控制台 / 日志桥。
 *
 * <p>这里统一处理 CLI 提示、日志初始化、用户可见输出与基础启动横幅。
 * 入口类不再直接关心日志回退、ANSI 颜色或版本展示细节。</p>
 */
public final class ClientConsole {
    private static final Scanner INPUT_SCANNER = new Scanner(System.in);

    private ClientConsole() {
    }

    public static void initializeLogger(boolean noColor) {
        DesktopLogManager.initialize(noColor);
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
}
