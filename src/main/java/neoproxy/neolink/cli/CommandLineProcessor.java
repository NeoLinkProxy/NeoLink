package neoproxy.neolink.cli;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

/**
 * 命令行解析器。
 *
 * <p>负责把 CLI 参数解释为显式状态变更，而不是让入口类直接拼装大量 setter 逻辑。
 * 这样既能保持参数语义稳定，也能让 CLI 与 GUI 共用同一份状态模型。</p>
 */
public final class CommandLineProcessor {

    private CommandLineProcessor() {
    }

    public static LaunchOptions applyCommandLineArgs(String[] args) {
        return parseCommandLineArgs(args);
    }

    static LaunchOptions parseCommandLineArgs(String[] args) {
        if (args == null) {
            return new LaunchOptions(false, false);
        }
        boolean hasKey = false;
        boolean hasLocalPort = false;
        boolean noColor = false;
        for (String arg : args) {
            if (arg.contains("=")) {
                parseKeyValueArgument(arg);
                if (arg.startsWith("--key=")) {
                    hasKey = true;
                } else if (arg.startsWith("--local-port=")) {
                    hasLocalPort = true;
                }
            } else {
                if ("--no-color".equals(arg)) {
                    noColor = true;
                }
                parseFlagArgument(arg);
            }
        }
        if (FeatureState.snapshot().guiMode()) {
            // GUI 仅保留中文界面，语言标志仍然只对 CLI 生效。
            RuntimeState.setLanguageData(LanguageData.getChineseLanguage());
        }
        return new LaunchOptions(hasKey && hasLocalPort && FeatureState.snapshot().guiMode(), noColor);
    }

    static void parseKeyValueArgument(String arg) {
        String[] parts = arg.split("=", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException(parts[0] + " requires a value.");
        }
        switch (parts[0]) {
            case "--key" -> ConnectionState.setKey(parts[1]);
            case "--local-port" -> ConnectionState.setLocalPort(parsePort(parts[1], "--local-port"));
            case "--output-file" -> FeatureState.setOutputFilePath(parts[1]);
            case "--node" -> ConnectionState.setSpecifiedNodeName(parts[1]);
            default -> {
            }
        }
    }

    static void parseFlagArgument(String arg) {
        switch (arg) {
            case "--en-us" -> RuntimeState.setLanguageData(new LanguageData());
            case "--zh-cn" -> RuntimeState.setLanguageData(LanguageData.getChineseLanguage());
            case "--no-color" -> {
            }
            case "--debug" -> FeatureState.setDebugMode(true);
            case "--no-show-conn" -> FeatureState.setShowConnection(false);
            case "--gui" -> FeatureState.setGuiMode(true);
            case "--nogui" -> FeatureState.setGuiMode(false);
            case "--disable-tcp" -> FeatureState.setDisableTCP(true);
            case "--disable-udp" -> FeatureState.setDisableUDP(true);
            case "--enable-pp" -> FeatureState.setEnableProxyProtocol(true);
            case "--test-update" -> FeatureState.setTestUpdate(true);
            case "--no-effect" -> FeatureState.setNoEffectMode(true);
            default -> {
            }
        }
    }

    static int parsePort(String value, String source) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(source + " must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(source + " must be an integer.", e);
        }
    }
}
