package neoproject.neolink.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import neoproject.neolink.ConfigOperator;
import neoproject.neolink.NeoLink;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoLink GUI 主窗口控制器 (最终版)。
 * - 移除自动重连 UI
 * - 启动/停止时输出提示到 UI 控制台
 * - 移除空的“高级设置”标题
 * - 异常不输出到 UI（由 NeoLink 内部处理）
 */
public class MainWindowController {
    private final Stage primaryStage;
    private final ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService logConsumerExecutor;
    private Future<?> currentTask = null;
    private TextField remoteDomainField;
    private TextField localPortField;
    private PasswordField accessKeyField;
    private WebView logWebView;
    private Button startButton;
    private Button stopButton;
    private volatile boolean isRunning = false;
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern ANSI_PATTERN = Pattern.compile("\033\\[([\\d;]*)m");
    private static final String[] ANSI_COLORS = new String[128];

    static {
        ANSI_COLORS[31] = "#ff5555";
        ANSI_COLORS[32] = "#50fa7b";
        ANSI_COLORS[33] = "#f1fa8c";
        ANSI_COLORS[34] = "#bd93f9";
        ANSI_COLORS[35] = "#ff79c6";
        ANSI_COLORS[36] = "#8be9fd";
    }

    public MainWindowController(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void show() {
        NeoLink.loggist = new QueueBasedLoggist();
        new GuiLogRedirector(LogMessageQueue::offer);
        NeoLink.detectLanguage();
        // 使用空输入流防止阻塞
        NeoLink.inputScanner = new Scanner(new ByteArrayInputStream(new byte[0]));
        ConfigOperator.readAndSetValue(); // 从 config.cfg 读取所有配置
        NeoLink.printLogo();
        NeoLink.printBasicInfo();

        Scene scene = new Scene(createMainLayout(), 950, 700);
        String css = Objects.requireNonNull(MainWindowController.class.getResource("/dark-theme-webview.css")).toExternalForm();
        scene.getStylesheets().add(css);
        primaryStage.setTitle("NeoLink - 内网穿透客户端");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> handleExit());
        primaryStage.show();
        startLogConsumer();
    }

    private void startLogConsumer() {
        logConsumerExecutor = new ScheduledThreadPoolExecutor(1);
        logConsumerExecutor.scheduleAtFixedRate(() -> {
            try {
                while (!LogMessageQueue.isEmpty()) {
                    String message = LogMessageQueue.take();
                    appendLogToWebView(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private BorderPane createMainLayout() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24));
        VBox topSection = new VBox(24);
        topSection.getChildren().addAll(createConnectionGroup());
        // 移除 createAdvancedGroup()
        root.setTop(topSection);
        VBox centerSection = createLogSection();
        root.setCenter(centerSection);
        HBox bottomBar = createBottomBar();
        root.setBottom(bottomBar);
        BorderPane.setMargin(topSection, new Insets(0, 0, 24, 0));
        return root;
    }

    private VBox createTitledGroup(Region content) {
        Label titleLabel = new Label("连接设置");
        titleLabel.getStyleClass().add("group-title");
        VBox group = new VBox(16);
        group.getChildren().addAll(titleLabel, content);
        return group;
    }

    private VBox createConnectionGroup() {
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(20);
        flowPane.setVgap(12);
        flowPane.setAlignment(Pos.CENTER_LEFT);
        flowPane.setPrefWrapLength(800);
        remoteDomainField = new TextField();
        remoteDomainField.setPromptText("远程服务器地址 (必填)");
        remoteDomainField.setText(NeoLink.remoteDomainName);
        remoteDomainField.setPrefWidth(220);
        localPortField = new TextField();
        localPortField.setPromptText("本地服务端口 (必填)");
        localPortField.setText(String.valueOf(NeoLink.localPort == -1 ? "" : NeoLink.localPort));
        localPortField.setPrefWidth(160);
        accessKeyField = new PasswordField();
        accessKeyField.setPromptText("访问密钥 (必填)");
        if (NeoLink.key != null) accessKeyField.setText(NeoLink.key);
        accessKeyField.setPrefWidth(220);
        flowPane.getChildren().addAll(
                createLabeledField("远程服务器:", remoteDomainField),
                createLabeledField("本地端口:", localPortField),
                createLabeledField("访问密钥:", accessKeyField)
        );
        return createTitledGroup(flowPane);
    }

    private HBox createLabeledField(String labelText, Control field) {
        Label label = new Label(labelText);
        HBox box = new HBox(8, label, field);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // 移除 createAdvancedGroup()

    private VBox createLogSection() {
        Label logTitle = new Label("运行日志");
        logTitle.getStyleClass().add("log-title");
        logWebView = new WebView();
        logWebView.setContextMenuEnabled(false);
        String initialHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            background-color: #0c0c0c;
                            color: #cccccc;
                            font-family: 'Consolas', 'Courier New', monospace;
                            font-size: 13px;
                            margin: 0;
                            padding: 12px;
                            white-space: pre-wrap;
                            word-wrap: break-word;
                        }
                    </style>
                </head>
                <body>
                </body>
                </html>
                """;
        logWebView.getEngine().loadContent(initialHtml);
        VBox logContainer = new VBox(8, logTitle, logWebView);
        VBox.setVgrow(logWebView, Priority.ALWAYS);
        return logContainer;
    }

    private HBox createBottomBar() {
        startButton = new Button("启动服务");
        startButton.getStyleClass().add("primary-button");
        stopButton = new Button("停止服务");
        stopButton.setDisable(true);
        startButton.setOnAction(e -> startService());
        stopButton.setOnAction(e -> stopService());
        HBox buttonBox = new HBox(16, startButton, stopButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        return buttonBox;
    }

    private void startService() {
        if (isRunning) return;
        if (!validateForm()) return;

        resetNeoLinkState();

        NeoLink.remoteDomainName = remoteDomainField.getText().trim();
        NeoLink.localPort = Integer.parseInt(localPortField.getText().trim());
        NeoLink.key = accessKeyField.getText();

        NeoLink.say("正在启动 NeoLink 服务...");

        isRunning = true;
        updateButtonState();

        currentTask = coreExecutor.submit(() -> {
            try {
                // 不再 throws Exception，内部已处理
                NeoLinkCoreRunner.runCore(
                        NeoLink.remoteDomainName,
                        NeoLink.localPort,
                        NeoLink.key
                );
            } finally {
                resetState();
            }
        });
    }

    private void resetNeoLinkState() {
        NeoLink.hookSocket = null;
        NeoLink.remotePort = 0;
        NeoLink.isReconnectedOperation = false;
        // 使用空输入流防止阻塞
        NeoLink.inputScanner = new Scanner(new ByteArrayInputStream(new byte[0]));
    }

    private void stopService() {
        NeoLink.say("正在关闭 NeoLink 服务...");

        if (!isRunning) return;

        // 【关键】请求停止重连循环
        NeoLinkCoreRunner.requestStop();

        // 【关键】主动关闭 socket，中断 receiveStr() 阻塞
        if (NeoLink.hookSocket != null) {
            try {
                NeoLink.hookSocket.close();
            } catch (Exception ignored) {
            }
        }

        // 停止心跳线程
        neoproject.neolink.threads.CheckAliveThread.stopThread();

        // 取消任务（辅助）
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }

        resetState();
        NeoLink.say("成功关闭 NeoLink 服务");
    }

    private void updateButtonState() {
        Platform.runLater(() -> {
            startButton.setDisable(true);
            stopButton.setDisable(false);
        });
    }

    private void resetState() {
        isRunning = false;
        Platform.runLater(() -> {
            startButton.setDisable(false);
            stopButton.setDisable(true);
        });
    }

    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();
        if (remoteDomainField.getText().trim().isEmpty()) {
            errors.append("• 请输入远程服务器地址。\n");
        }
        String portStr = localPortField.getText().trim();
        if (portStr.isEmpty()) {
            errors.append("• 请输入本地服务端口。\n");
        } else if (!PORT_PATTERN.matcher(portStr).matches()) {
            errors.append("• 本地端口必须是1-65535之间的数字。\n");
        } else {
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                errors.append("• 本地端口必须是1-65535之间的数字。\n");
            }
        }
        if (accessKeyField.getText().trim().isEmpty()) {
            errors.append("• 请输入访问密钥。\n");
        }
        if (!errors.isEmpty()) {
            showAlert(errors.toString().trim());
            return false;
        }
        return true;
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(primaryStage);
            alert.setTitle("输入错误");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void appendLogToWebView(String ansiText) {
        Platform.runLater(() -> {
            if (logWebView == null) return;
            Document doc = logWebView.getEngine().getDocument();
            if (doc == null) return;
            NodeList bodyList = doc.getElementsByTagName("body");
            if (bodyList.getLength() == 0) return;
            Element body = (Element) bodyList.item(0);
            Element logDiv = doc.createElement("div");
            if (!ansiText.contains("\033[")) {
                Text textNode = doc.createTextNode(ansiText);
                logDiv.appendChild(textNode);
            } else {
                parseAnsiAndAppend(doc, logDiv, ansiText);
            }
            body.appendChild(logDiv);
            logWebView.getEngine().executeScript(
                    "setTimeout(() => {" +
                            "document.body.style.display='none';" +
                            "document.body.offsetHeight;" +
                            "document.body.style.display='';" +
                            "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });" +
                            "}, 10);"
            );
        });
    }

    private void parseAnsiAndAppend(Document doc, Element parent, String ansiText) {
        Matcher matcher = ANSI_PATTERN.matcher(ansiText);
        int lastEnd = 0;
        String currentColor = null;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String plainText = ansiText.substring(lastEnd, matcher.start());
                appendTextWithColor(doc, parent, plainText, currentColor);
            }
            String codeStr = matcher.group(1);
            if ("0".equals(codeStr) || codeStr.isEmpty()) {
                currentColor = null;
            } else {
                String[] codes = codeStr.split(";");
                for (String code : codes) {
                    try {
                        int colorCode = Integer.parseInt(code);
                        if (colorCode >= 0 && colorCode < ANSI_COLORS.length && ANSI_COLORS[colorCode] != null) {
                            currentColor = ANSI_COLORS[colorCode];
                            break;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < ansiText.length()) {
            String plainText = ansiText.substring(lastEnd);
            appendTextWithColor(doc, parent, plainText, currentColor);
        }
    }

    private void appendTextWithColor(Document doc, Element parent, String text, String color) {
        if (color == null) {
            parent.appendChild(doc.createTextNode(text));
        } else {
            Element span = doc.createElement("span");
            span.setTextContent(text);
            span.setAttribute("style", "color: " + color + ";");
            parent.appendChild(span);
        }
    }

    private void handleExit() {
        stopService();
        if (logConsumerExecutor != null) {
            logConsumerExecutor.shutdownNow();
        }
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        coreExecutor.shutdownNow();
        Platform.exit();
        System.exit(0);
    }
}