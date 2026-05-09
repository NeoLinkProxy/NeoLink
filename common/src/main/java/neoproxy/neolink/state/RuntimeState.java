package neoproxy.neolink.state;

import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.util.LogSink;

/**
 * 运行时全局状态容器。
 * <p>
 * 与原始实现的差异：
 * <ul>
 *   <li>{@code Loggist} 替换为平台无关的 {@link LogSink} 接口</li>
 *   <li>{@code Scanner} 移除 — stdin 交互属于 Desktop/CLI 专有行为，由平台层管理</li>
 * </ul>
 */
public final class RuntimeState {
    private static volatile String tunnelAddress;
    private static volatile LogSink logSink;
    private static volatile LanguageData languageData;
    private static volatile boolean reconnectedOperation = false;

    private RuntimeState() {
    }

    public static String tunnelAddress() {
        return tunnelAddress;
    }

    public static void setTunnelAddress(String value) {
        tunnelAddress = value;
    }

    public static LogSink logSink() {
        return logSink;
    }

    public static void setLogSink(LogSink value) {
        logSink = value;
    }

    public static LanguageData languageData() {
        return languageData;
    }

    public static void setLanguageData(LanguageData value) {
        languageData = value;
    }

    public static boolean isReconnectedOperation() {
        return reconnectedOperation;
    }

    public static void setReconnectedOperation(boolean value) {
        reconnectedOperation = value;
    }
}
