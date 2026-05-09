package neoproxy.neolink.util;

/**
 * 日志输出抽象。
 * <p>
 * 将仅 JVM 的 {@code top.ceroxe.api.print.log.Loggist} 解耦为平台无关接口。
 * Desktop 实现桥接到 Loggist；Android 实现桥接到 android.util.Log。
 * <p>
 * 日志级别语义：
 * <ul>
 *   <li>INFO — 普通运行信息</li>
 *   <li>WARNING — 可恢复的异常状况</li>
 *   <li>ERROR — 不可恢复的错误</li>
 * </ul>
 */
public interface LogSink {

    enum Level {
        INFO, WARNING, ERROR
    }

    /**
     * 输出一条日志。
     *
     * @param level   日志级别
     * @param tag     日志标签（模块/来源标识）
     * @param message 日志正文
     */
    void log(Level level, String tag, String message);
}
