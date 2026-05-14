package neoproxy.neolink.util;

import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 调试输出桥。
 *
 * <p>调试信息只在 {@code debug mode} 打开时输出。若 {@link LogSink} 已注入，
 * 则统一交给日志系统做格式化与持久化；否则在非 GUI 模式下回退到标准输出。</p>
 *
 * <p>与原始实现的差异：将仅 JVM 的 {@code Loggist/LogType/State} 替换为
 * 平台无关的 {@link LogSink} 接口。</p>
 */
public final class Debugger {

    private Debugger() {
    }

    public static void debugOperation(Exception exception) {
        if (!FeatureState.snapshot().debugMode() || exception == null) {
            return;
        }

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        exception.printStackTrace(printWriter);
        String fullStackTrace = stringWriter.toString();

        LogSink sink = RuntimeState.logSink();
        if (sink != null) {
            // UI 模式的控制台只保留 UI subject；CLI 仍保留 DEBUG subject，方便排障时区分来源。
            sink.log(LogSink.Level.ERROR, logSubject(), fullStackTrace);
            return;
        }

        if (!FeatureState.snapshot().guiMode()) {
            System.err.println("[DEBUG-EXCEPTION] " + fullStackTrace);
        }
    }

    public static void debugOperation(String infoMessage) {
        if (!FeatureState.snapshot().debugMode()) {
            return;
        }

        LogSink sink = RuntimeState.logSink();
        if (sink != null) {
            sink.log(LogSink.Level.INFO, logSubject(), infoMessage);
            return;
        }

        if (!FeatureState.snapshot().guiMode()) {
            System.out.println("[DEBUG] " + infoMessage);
        }
    }

    private static String logSubject() {
        return FeatureState.snapshot().guiMode() ? "UI" : "DEBUG";
    }
}
