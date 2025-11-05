package neoproxy.neolink.gui;

import plethora.print.log.Loggist;
import plethora.print.log.State;

import java.io.File;
import java.util.regex.Pattern;

/**
 * 基于队列的 Loggist 实现，同时将日志写入文件。
 * 所有日志消息都会被格式化并发送到 LogMessageQueue 以供 GUI 显示，
 * 同时也会被移除颜色代码后委托给传入的 fileLoggist 实例写入到指定文件中。
 * 这确保了文件内容与 GUI 显示一致（但文件无颜色），并且支持 --output-file 参数。
 */
public class QueueBasedLoggist extends Loggist {

    // ANSI 转义序列的正则表达式，用于匹配颜色代码
    private static final Pattern ANSI_PATTERN = Pattern.compile("\033\\[([\\d;]*)m");
    private final Loggist fileLoggist; // 传入的、负责文件写入的 Loggist 实例

    // 构造函数接收一个已配置好文件路径的 Loggist 实例
    public QueueBasedLoggist(Loggist fileLoggist) {
        // 调用父类构造函数，传入一个虚拟文件，因为父类可能需要一个文件引用
        // 但我们不直接使用父类的 write 方法进行核心日志写入，所以这个文件实际不用于写入核心日志
        super(new File(System.getProperty("java.io.tmpdir"), "queue-based-dummy.log"));
        this.fileLoggist = fileLoggist;
    }

    @Override
    public void say(State state) {
        // 获取格式化后的日志字符串（包含时间戳、类型、来源等，可能包含 ANSI 代码）
        String logMessage = this.getLogString(state) + "\n";
        // 发送到 GUI 队列 (保持颜色)
        LogMessageQueue.offer(logMessage);
        // 写入文件（移除颜色后，通过传入的 fileLoggist 实例）
        // 从 logMessage 中移除 ANSI 代码
        String logMessageForFile = removeAnsiCodes(logMessage);
        // 使用 fileLoggist 的 write 方法写入文件
        // 注意：fileLoggist.write 本身可能在内部处理换行，我们传入 false，因为 logMessageForFile 已经包含了 \n
        fileLoggist.write(logMessageForFile, false);
    }

    @Override
    public void sayNoNewLine(State state) {
        // 获取格式化后的日志字符串（不带换行，可能包含 ANSI 代码）
        String logMessage = this.getLogString(state);
        // 发送到 GUI 队列 (保持颜色)
        LogMessageQueue.offer(logMessage);
        // 写入文件（移除颜色后，通过传入的 fileLoggist 实例）
        String logMessageForFile = removeAnsiCodes(logMessage);
        // fileLoggist.write 不会添加换行
        fileLoggist.write(logMessageForFile, false);
    }

    // 重写 write 方法，阻止其写入文件（因为我们已经在 say/sayNoNewLine 中通过 fileLoggist 处理了）
    // 这是为了防止父类在其他地方调用 write 时重复写入或干扰
    @Override
    public void write(String str, boolean isNewLine) {
        // Do nothing, 日志写入已在 say/sayNoNewLine 中通过 fileLoggist 完成
    }

    /**
     * 移除字符串中的 ANSI 转义序列。
     *
     * @param input 包含 ANSI 代码的原始字符串
     * @return 移除了 ANSI 代码的纯文本字符串
     */
    private String removeAnsiCodes(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // 使用预编译的正则表达式替换所有 ANSI 代码为空字符串
        return ANSI_PATTERN.matcher(input).replaceAll("");
    }
}