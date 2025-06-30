package neoproject.neolink.gui.utils;

import org.fxmisc.richtext.StyleClassedTextArea;
import java.io.OutputStream;
import java.io.PrintStream;

public class ConsoleRedirector {
    private final StyleClassedTextArea console;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    public ConsoleRedirector(StyleClassedTextArea console) {
        this.console = console;
    }

    public void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                appendText(String.valueOf((char) b), "out");
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendText(new String(b, off, len), "out");
            }
        };

        OutputStream err = new OutputStream() {
            @Override
            public void write(int b) {
                appendText(String.valueOf((char) b), "error");
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendText(new String(b, off, len), "error");
            }
        };

        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(err, true));
    }

    private void appendText(String text, String styleClass) {
        javafx.application.Platform.runLater(() -> {
            int start = console.getLength();
            console.appendText(text);
            console.setStyleClass(start, console.getLength(), styleClass);
            console.requestFollowCaret(); // 自动滚动到底部
        });
    }
}