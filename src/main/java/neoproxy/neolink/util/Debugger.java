package neoproxy.neolink.util;

import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import top.ceroxe.api.print.log.LogType;
import top.ceroxe.api.print.log.State;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 调试输出桥（debug output bridge）。
 *
 * <p>调试信息只在 `debug mode` 打开时输出。若 `Loggist` 已初始化，则统一交给日志系统做
 * 格式化与持久化；否则在 CLI 模式下直接回退到标准输出 / 标准错误。</p>
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

        if (RuntimeState.loggist() != null) {
            // 已有日志系统（logger ready）时，统一走日志桥，CLI / GUI 都能复用同一份输出。
            RuntimeState.loggist().say(new State(LogType.ERROR, "DEBUG", fullStackTrace));
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

        if (RuntimeState.loggist() != null) {
            RuntimeState.loggist().say(new State(LogType.INFO, "DEBUG", infoMessage));
            return;
        }

        if (!FeatureState.snapshot().guiMode()) {
            System.out.println("[DEBUG] " + infoMessage);
        }
    }
}
