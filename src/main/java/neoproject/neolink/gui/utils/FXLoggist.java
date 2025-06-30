package neoproject.neolink.gui.utils;

import javafx.application.Platform;
import neoproject.neolink.gui.controller.MainController;
import plethora.print.Printer;
import plethora.print.log.LogType;
import plethora.print.log.Loggist;
import plethora.print.log.State;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FXLoggist extends Loggist {
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[([0-9]{1,2}(;[0-9]{1,2})?)?[m|K]");
    private static final Pattern COLOR_PATTERN = Pattern.compile("\u001B\\[(\\d+)m");

    private final StyleClassedTextArea consoleArea;
    private String currentStyle = "log-default";

    public FXLoggist(File logFile, StyleClassedTextArea consoleArea) {
        super(logFile);
        this.consoleArea = consoleArea;
        this.disableColor(); // 禁用控制台彩色输出
    }

    @Override
    public void say(State state) {
        String coloredText = super.getLogString(state);
        String plainText = super.getNoColString(state);

        // 确保每条日志以换行符结束
        if (!coloredText.endsWith("\n")) {
            coloredText += "\n";
            plainText += "\n";
        }

        // 处理彩色输出
        appendColoredText(coloredText);

        // 写入原始日志文件
        super.write(plainText, true);
    }

    @Override
    public void sayNoNewLine(State state) {
        String coloredText = super.getLogString(state);
        String plainText = super.getNoColString(state);

        // 处理彩色输出
        appendColoredText(coloredText);

        // 写入原始日志文件
        super.write(plainText, false);
    }

    private void appendColoredText(String text) {
        Platform.runLater(() -> {
            List<AnsiSegment> segments = parseAnsiSegments(text);

            for (AnsiSegment segment : segments) {
                if (!segment.text.isEmpty()) {
                    int start = consoleArea.getLength();
                    consoleArea.appendText(segment.text);
                    consoleArea.setStyleClass(start, consoleArea.getLength(), segment.styleClass);
                }
            }
        });
    }

    private List<AnsiSegment> parseAnsiSegments(String text) {
        List<AnsiSegment> segments = new ArrayList<>();
        Matcher matcher = ANSI_PATTERN.matcher(text);

        int lastIndex = 0;

        while (matcher.find()) {
            // 添加前一段普通文本
            if (matcher.start() > lastIndex) {
                String plainText = text.substring(lastIndex, matcher.start());
                segments.add(new AnsiSegment(plainText, currentStyle));
            }

            // 处理ANSI转义序列
            String ansiCode = matcher.group();
            if (ansiCode.equals("\u001B[0m")) {
                currentStyle = "log-default"; // 重置样式
            } else {
                Matcher colorMatcher = COLOR_PATTERN.matcher(ansiCode);
                if (colorMatcher.find()) {
                    String colorCode = colorMatcher.group(1);
                    currentStyle = getStyleClassForColorCode(colorCode);
                }
            }

            lastIndex = matcher.end();
        }

        // 添加最后一段文本
        if (lastIndex < text.length()) {
            String remainingText = text.substring(lastIndex);
            segments.add(new AnsiSegment(remainingText, currentStyle));
        }

        return segments;
    }

    private String getStyleClassForColorCode(String colorCode) {
        if (colorCode == null) return "log-default";

        try {
            int code = Integer.parseInt(colorCode);
            switch (code) {
                case Printer.color.RED: return "log-error";
                case Printer.color.YELLOW: return "log-warning";
                case Printer.color.ORANGE: return "log-orange";
                case Printer.color.BLUE: return "log-info";
                case Printer.color.PURPLE: return "log-purple";
                case Printer.color.GREEN: return "log-success";
                default: return "log-default";
            }
        } catch (NumberFormatException e) {
            return "log-default";
        }
    }

    // 辅助类，表示文本段及其样式
    private static class AnsiSegment {
        String text;
        String styleClass;

        AnsiSegment(String text, String styleClass) {
            this.text = text;
            this.styleClass = styleClass;
        }
    }
}