package neoproxy.neolink.util;

/**
 * 用户可见消息输出抽象（message sink abstraction）。
 * <p>
 * 将 CLI 专有的 {@code ClientConsole.say()} 解耦为平台无关接口。
 * Desktop CLI 实现输出到终端；GUI 实现推送到 ViewModel；Android 实现可选 Toast/Snackbar。
 * <p>
 * 与 {@link LogSink} 的区别：LogSink 面向开发者日志，MessageSink 面向终端用户可见消息。
 */
public interface MessageSink {

    /**
     * 输出一条面向用户的消息。
     *
     * @param message 消息文本
     * @param level   消息重要程度（映射到日志级别语义）
     */
    void say(String message, LogSink.Level level);

    /**
     * 输出一条 INFO 级别的用户消息。
     */
    default void say(String message) {
        say(message, LogSink.Level.INFO);
    }
}
