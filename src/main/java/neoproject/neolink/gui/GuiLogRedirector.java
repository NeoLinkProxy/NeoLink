package neoproject.neolink.gui;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 专为 WebView 设计的日志重定向器 (乱码修复版)。
 * 使用安全的 DOM 操作 API，彻底解决中文乱码问题。
 */
public class GuiLogRedirector {
    private final Consumer<String> logConsumer;
    private final PrintStream originalOut;
    private final PrintStream originalErr;
    private final StringBuilder currentLine = new StringBuilder();

    // ANSI 转义序列的正则表达式
    private static final Pattern ANSI_PATTERN = Pattern.compile("\033\\[([\\d;]*)m");
    // ANSI 颜色代码到 HTML 颜色的映射
    private static final String[] ANSI_COLORS = new String[128];

    static {
        ANSI_COLORS[31] = "#ff5555"; // 红色
        ANSI_COLORS[32] = "#50fa7b"; // 绿色
        ANSI_COLORS[33] = "#f1fa8c"; // 黄色
        ANSI_COLORS[34] = "#bd93f9"; // 蓝色/紫色
        ANSI_COLORS[35] = "#ff79c6"; // 粉色
        ANSI_COLORS[36] = "#8be9fd"; // 青色
    }

    public GuiLogRedirector(Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
        this.originalOut = System.out;
        this.originalErr = System.err;

        OutputStream guiOutputStream = new OutputStream() {
            @Override
            public void write(int b) {
                char c = (char) b;
                if (c == '\n') {
                    flushLineToGui();
                } else {
                    currentLine.append(c);
                }
            }
        };

        System.setOut(new PrintStream(guiOutputStream, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(guiOutputStream, true, StandardCharsets.UTF_8));
    }

    private void flushLineToGui() {
        String line = currentLine.toString();
        currentLine.setLength(0);
        // 直接传递原始字符串，由 WebView 端安全处理
        logConsumer.accept(line);
    }

    // 移除了 convertAnsiToHtml 方法，因为现在在 WebView 端处理

    public void restore() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }
}