package neoproject.neolink.gui;

import plethora.print.log.Loggist;
import plethora.print.log.State;

import java.io.File;

/**
 * 基于队列的 Loggist 实现。
 * 所有日志消息都会被格式化并发送到 LogMessageQueue，而不是写入文件或控制台。
 */
public class QueueBasedLoggist extends Loggist {
    public QueueBasedLoggist() {
        // 调用父类构造，传入一个虚拟文件
        super(new File(System.getProperty("java.io.tmpdir"), "queue-based-dummy.log"));
    }

    @Override
    public void say(State state) {
        // 复用父类的 getLogString 方法来获取带 ANSI 颜色的完整日志字符串
        String logMessage = this.getLogString(state) + "\n";
        LogMessageQueue.offer(logMessage);
    }

    @Override
    public void sayNoNewLine(State state) {
        // 复用父类的 getLogString 方法
        String logMessage = this.getLogString(state);
        LogMessageQueue.offer(logMessage);
    }

    // 禁用文件写入和控制台输出
    @Override
    public void write(String str, boolean isNewLine) {
        // Do nothing, don't write to file
    }

    // 重写父类的 say 方法，阻止其向 System.out 打印
    // 父类的 say 方法会调用 System.out.println(getLogString(state)) 和 write(...)
    // 我们已经覆盖了这两个方法，所以这里不需要额外操作。
    // 但为了绝对安全，可以显式地不调用 super.say()。
}