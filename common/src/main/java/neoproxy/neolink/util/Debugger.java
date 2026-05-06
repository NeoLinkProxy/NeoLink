package neoproxy.neolink.util;

import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 调试输出桥（debug output bridge）。
 *
 * <p>调试信息只在 {@code debug mode} 打开时输出。若 {@link LogSink} 已注入，
 * 则统一交给日志系统做格式化与持久化；否则在非 GUI 模式下回退到标准输出。</p>
 *
 * <p>与原始实现的差异：将 JVM-only 的 {@code Loggist/LogType/State} 替换为
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
            // 已有日志系统（logger ready）时，统一走日志桥，CLI / GUI / Android 都能复用同一份输出。
            sink.log(LogSink.Level.ERROR, "DEBUG", fullStackTrace);
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
            sink.log(LogSink.Level.INFO, "DEBUG", infoMessage);
            return;
        }

        if (!FeatureState.snapshot().guiMode()) {
            System.out.println("[DEBUG] " + infoMessage);
        }
    }
}
