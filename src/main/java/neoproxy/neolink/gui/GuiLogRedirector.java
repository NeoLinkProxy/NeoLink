package neoproxy.neolink.gui;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 专为 WebView 设计的日志重定向器 (UTF-8 字节流修复版)。
 * 修复了之前将单个字节强制转为 char 导致的中文乱码问题。
 */
public class GuiLogRedirector {
    private final Consumer<String> logConsumer;
    private final PrintStream originalOut;
    private final PrintStream originalErr;

    // 使用 ByteArrayOutputStream 来缓存字节，直到遇到换行
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public GuiLogRedirector(Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
        this.originalOut = System.out;
        this.originalErr = System.err;

        OutputStream guiOutputStream = new OutputStream() {
            @Override
            public void write(int b) {
                // 这是一个同步方法，为了线程安全，建议加锁，或者简单处理
                synchronized (buffer) {
                    if (b == '\n') {
                        flushBuffer();
                    } else {
                        buffer.write(b);
                    }
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                synchronized (buffer) {
                    // 扫描字节数组查找换行符
                    for (int i = off; i < off + len; i++) {
                        if (b[i] == '\n') {
                            buffer.write(b, off, i - off);
                            flushBuffer();
                            off = i + 1;
                        }
                    }
                    if (off < off + len) {
                        buffer.write(b, off, off + len - off);
                    }
                }
            }
        };

        // 强制使用 UTF-8 创建 PrintStream
        try {
            PrintStream utf8Stream = new PrintStream(guiOutputStream, true, StandardCharsets.UTF_8);
            System.setOut(utf8Stream);
            System.setErr(utf8Stream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void flushBuffer() {
        if (buffer.size() > 0) {
            // 核心修复：将缓存的字节数组统一按 UTF-8 解码为字符串
            String line = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            // 去除可能残留的 \r
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            logConsumer.accept(line);
        }
    }

    public void restore() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }
}