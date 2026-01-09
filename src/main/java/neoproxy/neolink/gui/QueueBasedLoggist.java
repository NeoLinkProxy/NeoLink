package neoproxy.neolink.gui;

import fun.ceroxe.api.print.log.Loggist;
import fun.ceroxe.api.print.log.State;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * 终极修复版 QueueBasedLoggist。
 * 策略：
 * 1. GUI：直接发送格式化好的 String 到队列，绕过 System.out，确保绝对无乱码。
 * 2. 文件：委托给底层 fileLoggist.say() 确保能写入文件。
 * 3. 冲突解决：在调用底层写文件时，临时屏蔽 System.out，防止日志被重定向器捕获导致重复和乱码。
 */
public class QueueBasedLoggist extends Loggist {

    // 一个“黑洞”PrintStream，什么都不做
    private static final PrintStream DUMMY_OUT = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    });
    private final Loggist fileLoggist;

    public QueueBasedLoggist(Loggist fileLoggist) {
        super(new File(System.getProperty("java.io.tmpdir"), "queue-based-dummy.log"));
        this.fileLoggist = fileLoggist;
    }

    @Override
    public void say(State state) {
        // --- 1. GUI 部分 (保持原样，清晰无乱码) ---
        // 直接将格式化好的字符串（带颜色）发送给 GUI 队列
        // 这样不经过字节流转换，绝对不会有乱码
        LogMessageQueue.offer(this.getLogString(state));

        // --- 2. 文件 部分 (使用底层逻辑，但屏蔽控制台输出) ---
        // 我们需要 fileLoggist 把日志放进它的异步写文件队列里。
        // 但是 fileLoggist.say() 默认会 System.out.println，这会导致 GUI 收到重复且可能乱码的内容。
        // 所以我们用“移花接木”法，暂时把 System.out 指向黑洞。

        PrintStream originalOut = System.out;
        // 加锁防止多线程竞争导致 System.out 状态错乱
        synchronized (System.out) {
            try {
                System.setOut(DUMMY_OUT); // 切换到静音模式
                fileLoggist.say(state);   // 执行写文件逻辑（控制台输出被吞掉，文件写入逻辑正常执行）
            } finally {
                System.setOut(originalOut); // 立即恢复，以免影响其他非日志的输出
            }
        }
    }

    @Override
    public void sayNoNewLine(State state) {
        // 同理处理不换行的情况
        LogMessageQueue.offer(this.getLogString(state));

        PrintStream originalOut = System.out;
        synchronized (System.out) {
            try {
                System.setOut(DUMMY_OUT);
                fileLoggist.sayNoNewLine(state);
            } finally {
                System.setOut(originalOut);
            }
        }
    }

    @Override
    public void write(String str, boolean isNewLine) {
        // 直接写入不做特殊处理，通常业务逻辑主要走 say()
        fileLoggist.write(str, isNewLine);
    }

    @Override
    public void close() {
        fileLoggist.close();
        super.close();
    }
}