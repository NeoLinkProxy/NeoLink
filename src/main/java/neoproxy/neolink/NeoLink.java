package neoproxy.neolink;

import neoproxy.neolink.cli.ClientConsole;
import neoproxy.neolink.cli.CommandLineProcessor;
import neoproxy.neolink.cli.LaunchOptions;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.core.NeoLinkCoreRunner;
import neoproxy.neolink.gui.ComposeEntryKt;
import neoproxy.neolink.state.ConnectionState;
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;
import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.node.NodeWorkflow;
import top.ceroxe.api.neolink.NeoNode;

import java.util.function.IntConsumer;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * NeoLink 客户端入口（application entry point）。
 *
 * <p>本类现在只保留启动编排（startup orchestration）职责：初始化环境、选择 GUI / CLI、
 * 串联配置加载与 tunnel 启动。命令行解析、控制台输出、节点工作流等工具职责已经下沉到独立类。</p>
 */
public final class NeoLink {
    public static final String CLIENT_FILE_PREFIX = "NeoLink-";
    public static final String TEST_UPDATE_VERSION = "0.0.1";
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final int INVALID_LOCAL_PORT = -1;
    public static final String ASCII_LOGO = """
            
               _____
              / ____|
             | |        ___   _ __    ___   __  __   ___
             | |       / _ \\ | '__|  / _ \\  \\ \\\\/ /  / _ \\
             | |____  |  __/ | |    | (_) |  >  <  |  __/
              \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
            
            
            """;
    private static boolean shouldAutoStartInGUI = false;
    private static boolean noColor = false;
    private static volatile IntConsumer exitHandler = System::exit;

    private NeoLink() {
    }

    public static boolean shouldAutoStart() {
        return shouldAutoStartInGUI;
    }

    public static void setExitHandler(IntConsumer customExitHandler) {
        exitHandler = customExitHandler == null ? System::exit : customExitHandler;
    }

    public static void resetExitHandler() {
        exitHandler = System::exit;
    }

    public static void requestExit(int exitCode) {
        exitHandler.accept(exitCode);
    }

    public static void main(String[] args) {
        LaunchOptions launchOptions;
        ConfigOperator.initEnvironment();
        try {
            ConfigOperator.readAndSetValue();
            launchOptions = CommandLineProcessor.applyCommandLineArgs(args);
            shouldAutoStartInGUI = launchOptions.autoStartInGui();
        } catch (IllegalArgumentException e) {
            System.err.println("[NeoLink] " + e.getMessage());
            requestExit(-1);
            return;
        }

        debugOperation("Entering main() method.");
        var features = FeatureState.snapshot();
        debugOperation("Mode: " + (features.guiMode() ? "GUI" : "CLI") + ", Debug: " + features.debugMode());

        if (features.guiMode()) {
            ComposeEntryKt.main(args);
            requestExit(0);
            return;
        }

        ClientConsole.initializeLogger(launchOptions.noColor());
        LanguageManager.detectLanguage();
        NodeWorkflow.fetchAndSaveNodes();
        NeoNode selectedNode = NodeWorkflow.loadSelectedNodeConfiguration();

        if (!RuntimeState.isReconnectedOperation()) {
            ClientConsole.printLogo();
            ClientConsole.printBasicInfo();
        }

        try {
            ClientConsole.requestAccessKeyIfMissing();
            ClientConsole.requestLocalPortIfMissing();
            NeoLinkCoreRunner.runCore(NodeWorkflow.buildTunnelConfig(selectedNode));
        } catch (Exception e) {
            debugOperation(e);
            ClientConsole.exitAndFreeze(-1);
        }
    }
}
