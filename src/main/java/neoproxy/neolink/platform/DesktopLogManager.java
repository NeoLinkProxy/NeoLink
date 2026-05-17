package neoproxy.neolink.platform;

import neoproxy.neolink.app.ApplicationFiles;
import neoproxy.neolink.state.RuntimeState;
import neoproxy.neolink.util.LogSink;
import top.ceroxe.api.print.log.LogType;
import top.ceroxe.api.print.log.Loggist;
import top.ceroxe.api.print.log.State;
import top.ceroxe.api.utils.TimeUtils;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 桌面端日志生命周期管理器。
 *
 * <p>日志文件初始化只能有一个入口，否则 GUI 和 CLI 会互相覆盖
 * {@link RuntimeState} 中的 sink，造成重复打开文件、早期日志丢失和输出位置不可预测。
 * 这里负责创建文件 sink；GUI 只通过 {@link #attachMirror(LogSink)} 增加界面镜像。</p>
 */
public final class DesktopLogManager {
    private static final String UI_LOG_PREFIX = "UI-";
    private static final String TUNNEL_LOG_SUFFIX = ".log";
    private static final String TUNNEL_LOG_SUBJECT = "HOST-CLIENT";
    private static final String REDACTION_MARKER = "***";
    private static final Pattern CHINESE_KEY_WRITE_PATTERN = Pattern.compile(
            "(((?:写入|保存|更新|设置|使用|加载|读取)\\s*密钥\\s*(?:为|是|:|：|=)?\\s*)|(?:密钥\\s*(?:为|是|:|：|=)\\s*))([^\\s,，。;；]+)"
    );
    private static final Pattern ENGLISH_KEY_WRITE_PATTERN = Pattern.compile(
            "((?i)(?:write|wrote|save|saved|persist|persisted|set|update|updated|load|loaded|use|using)\\s+(?:key|api[-_ ]?key|access[-_ ]?key)\\s*(?:to|as|:|=)?\\s*)([^\\s,，。;；]+)"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "((?i)(?:password|passwd|pwd|token|session[-_ ]?token|secret|authorization|auth[-_ ]?token|cookie|private[-_ ]?key)\\s*(?:=|:|：)\\s*)([^\\s,，。;；]+)"
    );
    private static final Pattern CHINESE_PASSWORD_PATTERN = Pattern.compile(
            "((?:密码|口令|令牌|会话令牌)\\s*(?:为|是|:|：|=)?\\s*)([^\\s,，。;；]+)"
    );
    private static final Pattern PROXY_CREDENTIAL_PATTERN = Pattern.compile("(@[^;\\s]+;)([^\\s,，。;；]+)");
    private static final Pattern AUTHORIZATION_BEARER_PATTERN = Pattern.compile("((?i)Bearer\\s+)([A-Za-z0-9._~+\\-/]+=*)");

    private static final Object LOCK = new Object();
    private static final ConcurrentMap<String, Loggist> tunnelLoggists = new ConcurrentHashMap<>();
    private static Loggist uiLoggist;
    private static LogSink fileSink;
    private static LogSink mirrorSink;
    private static LogSink preservedSink;
    private static LogSink compositeSink;
    private static boolean colorDisabled;

    private DesktopLogManager() {
    }

    public static void initialize(boolean noColor) {
        synchronized (LOCK) {
            if (fileSink == null) {
                uiLoggist = openLoggist(noColor);
                colorDisabled = noColor;
                fileSink = (level, tag, message) -> uiLoggist.say(new State(toLogType(level), tag, message));
                preservedSink = null;
            }
            installCompositeSink();
        }
    }

    public static void attachMirror(LogSink mirror) {
        synchronized (LOCK) {
            LogSink currentSink = RuntimeState.logSink();
            if (currentSink != null && currentSink != compositeSink && currentSink != fileSink) {
                preservedSink = currentSink;
            }
            mirrorSink = mirror;
            installCompositeSink();
        }
    }

    public static void openTunnelLog(String tunnelId, String tunnelName, boolean noColor) {
        requireTunnelId(tunnelId);
        String normalizedName = normalizeTunnelLogFileName(tunnelName);
        Loggist nextLoggist = openLoggist(
                ApplicationFiles.tunnelLogFile(normalizedName + "-" + TimeUtils.getCurrentTimeAsFileName(false) + TUNNEL_LOG_SUFFIX),
                noColor
        );
        Loggist previousLoggist = tunnelLoggists.put(tunnelId, nextLoggist);
        closeQuietly(previousLoggist);
    }

    public static void logTunnel(String tunnelId, LogType level, String message) {
        requireTunnelId(tunnelId);
        Loggist loggist = tunnelLoggists.get(tunnelId);
        if (loggist != null) {
            loggist.say(new State(level, TUNNEL_LOG_SUBJECT, sanitizeForLog(message)));
        }
    }

    public static String formatForUi(LogSink.Level level, String tag, String message) {
        return formatForUi(toLogType(level), tag, message);
    }

    public static String formatForUi(LogType level, String tag, String message) {
        String sanitizedMessage = sanitizeForLog(message);
        State state = new State(level, tag, sanitizedMessage);
        synchronized (LOCK) {
            if (uiLoggist == null) {
                return sanitizedMessage;
            }
            return colorDisabled ? uiLoggist.getNoColString(state) : uiLoggist.getLogString(state);
        }
    }

    public static String sanitizeForLog(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String sanitized = message;
        sanitized = maskKeyValues(sanitized, CHINESE_KEY_WRITE_PATTERN);
        sanitized = maskKeyValues(sanitized, ENGLISH_KEY_WRITE_PATTERN);
        sanitized = maskWholeValues(sanitized, SENSITIVE_ASSIGNMENT_PATTERN);
        sanitized = maskWholeValues(sanitized, CHINESE_PASSWORD_PATTERN);
        sanitized = maskWholeValues(sanitized, PROXY_CREDENTIAL_PATTERN);
        sanitized = maskWholeValues(sanitized, AUTHORIZATION_BEARER_PATTERN);
        return sanitized;
    }

    public static void closeTunnelLog(String tunnelId) {
        if (tunnelId == null || tunnelId.isBlank()) {
            return;
        }
        closeQuietly(tunnelLoggists.remove(tunnelId));
    }

    public static void shutdown() {
        synchronized (LOCK) {
            tunnelLoggists.values().forEach(DesktopLogManager::closeQuietly);
            tunnelLoggists.clear();
            closeQuietly(uiLoggist);
            uiLoggist = null;
            fileSink = null;
            mirrorSink = null;
            preservedSink = null;
            compositeSink = null;
            colorDisabled = false;
            RuntimeState.setLogSink(null);
        }
    }

    public static boolean isValidTunnelLogFileName(String tunnelName) {
        try {
            return tunnelName != null && tunnelName.equals(normalizeTunnelLogFileName(tunnelName));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Loggist openLoggist(boolean noColor) {
        File logFile = ApplicationFiles.uiLogFile(UI_LOG_PREFIX + TimeUtils.getCurrentTimeAsFileName(false) + TUNNEL_LOG_SUFFIX);
        return openLoggist(logFile, noColor);
    }

    private static Loggist openLoggist(File logFile, boolean noColor) {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Failed to create log directory: " + parent.getAbsolutePath());
        }

        Loggist loggist = new Loggist(logFile);
        if (noColor) {
            loggist.disableColor();
        }
        loggist.openWriteChannel();
        return loggist;
    }

    private static String normalizeTunnelLogFileName(String tunnelName) {
        if (tunnelName == null) {
            throw new IllegalArgumentException("隧道名称不能为空。");
        }
        String normalized = tunnelName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("隧道名称不能为空。");
        }
        if (!normalized.equals(new File(normalized).getName())) {
            throw new IllegalArgumentException("隧道名称不能包含路径分隔符。");
        }
        if (normalized.chars().anyMatch(DesktopLogManager::isInvalidFileNameCharacter)) {
            throw new IllegalArgumentException("隧道名称不能包含文件名非法字符：\\ / : * ? \" < > |。");
        }
        if (normalized.endsWith(".") || normalized.endsWith(" ")) {
            throw new IllegalArgumentException("隧道名称不能以空格或点号结尾。");
        }
        if (isReservedWindowsDeviceName(normalized)) {
            throw new IllegalArgumentException("隧道名称不能使用 Windows 保留设备名。");
        }
        return normalized;
    }

    private static boolean isInvalidFileNameCharacter(int codePoint) {
        return codePoint < 32
                || codePoint == '\\'
                || codePoint == '/'
                || codePoint == ':'
                || codePoint == '*'
                || codePoint == '?'
                || codePoint == '"'
                || codePoint == '<'
                || codePoint == '>'
                || codePoint == '|';
    }

    private static boolean isReservedWindowsDeviceName(String fileName) {
        String baseName = fileName;
        int dotIndex = baseName.indexOf('.');
        if (dotIndex >= 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        String upper = baseName.toUpperCase(java.util.Locale.ROOT);
        if ("CON".equals(upper) || "PRN".equals(upper) || "AUX".equals(upper) || "NUL".equals(upper)) {
            return true;
        }
        if (upper.length() == 4 && (upper.startsWith("COM") || upper.startsWith("LPT"))) {
            char last = upper.charAt(3);
            return last >= '1' && last <= '9';
        }
        return false;
    }

    private static void requireTunnelId(String tunnelId) {
        if (tunnelId == null || tunnelId.isBlank()) {
            throw new IllegalArgumentException("Tunnel id cannot be blank.");
        }
    }

    private static void closeQuietly(Loggist loggist) {
        if (loggist == null) {
            return;
        }
        try {
            loggist.close();
        } catch (Exception ignore) {
            // 关闭日志不能打断隧道生命周期；Loggist 自身会尽力 flush 剩余队列。
        }
    }

    private static void installCompositeSink() {
        compositeSink = (level, tag, message) -> {
            String sanitizedMessage = sanitizeForLog(message);
            LogSink currentMirror;
            LogSink currentFile;
            LogSink currentPreserved;
            synchronized (LOCK) {
                currentMirror = mirrorSink;
                currentFile = fileSink;
                currentPreserved = preservedSink;
            }
            if (currentMirror != null) {
                currentMirror.log(level, tag, formatForUi(level, tag, sanitizedMessage));
            }
            if (currentFile != null) {
                currentFile.log(level, tag, sanitizedMessage);
            }
            if (currentPreserved != null) {
                currentPreserved.log(level, tag, sanitizedMessage);
            }
        };
        RuntimeState.setLogSink(compositeSink);
    }

    private static String maskKeyValues(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(1) + revealKeyPrefix(matcher.group(matcher.groupCount()))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String maskWholeValues(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(1) + REDACTION_MARKER));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String revealKeyPrefix(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return REDACTION_MARKER;
        }
        String value = rawValue.trim();
        int prefixLength = Math.min(3, value.codePointCount(0, value.length()));
        int prefixEnd = value.offsetByCodePoints(0, prefixLength);
        return value.substring(0, prefixEnd) + REDACTION_MARKER;
    }

    private static LogType toLogType(LogSink.Level level) {
        return switch (level) {
            case WARNING -> LogType.WARNING;
            case ERROR -> LogType.ERROR;
            default -> LogType.INFO;
        };
    }
}
