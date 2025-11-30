package neoproxy.neolink.gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import neoproxy.neolink.ConfigOperator;
import neoproxy.neolink.NeoLink;
import neoproxy.neolink.threads.CheckAliveThread;
import plethora.print.log.Loggist;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static neoproxy.neolink.InternetOperator.sendStr;
import static neoproxy.neolink.NeoLink.*;

/**
 * NeoLink GUI 主窗口控制器 (性能优化修复版)
 * 修复了大量日志涌入导致UI卡顿的问题：
 * 1. 引入 pendingLogBuffer 进行日志缓冲
 * 2. 使用 requestUiUpdate 进行批处理渲染
 * 3. 在 JS 端控制最大行数 (1000行)
 */
public class MainWindowController {
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");
    // 最大日志显示条数
    private static final int MAX_LOG_ENTRIES = 1000;
    private static boolean shouldAutoStart = false;
    private final Stage primaryStage;
    private final ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
    // ==================== 性能优化新增变量 ====================
    // 缓存待显示的 HTML 片段队列
    private final ConcurrentLinkedQueue<String> pendingLogBuffer = new ConcurrentLinkedQueue<>();
    // 原子标记，防止 UI 线程被 runLater 淹没
    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);
    private ExecutorService logConsumerExecutor;
    private Future<?> currentTask = null;
    private TextField remoteDomainField;
    private TextField localPortField;
    private PasswordField accessKeyField;
    private WebView logWebView;
    private WebEngine webEngine; // 缓存 WebEngine 引用
    private Button startButton;
    private Button stopButton;
    private volatile boolean isRunning = false;
    private boolean isMaximized = false;
    private double xOffset = 0;
    private double yOffset = 0;
    private TextField localDomainField;
    private TextField hostHookPortField;
    private TextField hostConnectPortField;
    private Label tcpCheckMark;
    private Label udpCheckMark;
    private Label reconnectCheckMark;
    // =======================================================

    public MainWindowController(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public static void setAutoStart(boolean autoStart) {
        shouldAutoStart = autoStart;
    }

    public void show() {
        try {
            NeoLink.initializeLogger();
        } catch (Exception e) {
            System.err.println("Failed to initialize NeoLink logger: " + e.getMessage());
        }

        Loggist fileLoggist = NeoLink.loggist;
        new GuiLogRedirector(LogMessageQueue::offer);
        NeoLink.loggist = new QueueBasedLoggist(fileLoggist);

        NeoLink.detectLanguage();
        NeoLink.inputScanner = new Scanner(new ByteArrayInputStream(new byte[0]));
        ConfigOperator.readAndSetValue();
        NeoLink.printLogo();
        NeoLink.printBasicInfo();

        primaryStage.initStyle(StageStyle.UNDECORATED);
        Scene scene = new Scene(createMainLayout(), 950, 700);

        try {
            String css = Objects.requireNonNull(MainWindowController.class.getResource("/dark-theme-webview.css")).toExternalForm();
            scene.getStylesheets().add(css);
            String contextMenuCss = Objects.requireNonNull(MainWindowController.class.getResource("/dark-context-menu.css")).toExternalForm();
            scene.getStylesheets().add(contextMenuCss);
        } catch (Exception e) {
            System.err.println("CSS load warning: " + e.getMessage());
        }

        scene.setOnDragOver(Event::consume);
        scene.setOnDragEntered(Event::consume);
        scene.setOnDragExited(Event::consume);
        scene.setOnDragDropped(event -> {
            event.setDropCompleted(false);
            event.consume();
        });

        try {
            Image appIcon = new Image(Objects.requireNonNull(
                    MainWindowController.class.getResourceAsStream("/logo.png")
            ));
            primaryStage.getIcons().add(appIcon);
        } catch (Exception ignored) {
        }

        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> handleExit());
        primaryStage.show();

        // 滚动条处理保持不变
        Platform.runLater(this::hideWebViewScrollBars);
        Timeline scrollbarHider = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            if (logWebView != null) {
                hideWebViewScrollBars();
            }
        }));
        scrollbarHider.setCycleCount(Timeline.INDEFINITE);
        scrollbarHider.play();

        startLogConsumer();
        setupWindowResizeHandlers(scene);

        if (shouldAutoStart) {
            Platform.runLater(() -> {
                if (NeoLink.key != null) accessKeyField.setText(NeoLink.key);
                if (NeoLink.localPort != -1) localPortField.setText(String.valueOf(NeoLink.localPort));
                startService();
            });
        }
    }

    private void hideWebViewScrollBars() {
        if (logWebView == null) return;
        try {
            for (Node node : logWebView.lookupAll("*")) {
                if (node.getClass().getName().contains("ScrollBar")) {
                    node.setVisible(false);
                    node.setManaged(false);
                    node.setOpacity(0);
                    node.resize(0, 0);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ... (setupWindowResizeHandlers, createCustomTitleBar, toggleMaximize 等 UI 代码保持不变) ...
    // 为了节省篇幅，这里省略了未修改的UI布局代码，请保留你原有的 createCustomTitleBar 等方法
    private void setupWindowResizeHandlers(Scene scene) {
        // [保持原样]
        final double[] startX = new double[1];
        final double[] startY = new double[1];
        final double[] initialWidth = new double[1];
        final double[] initialHeight = new double[1];
        final double[] initialX = new double[1];
        final double[] initialY = new double[1];
        final double EDGE_SIZE = 5;
        final String[] resizeDirection = {null};

        scene.setOnMouseMoved(event -> {
            if (isMaximized) return;
            double x = event.getX();
            double y = event.getY();
            double width = primaryStage.getWidth();
            double height = primaryStage.getHeight();
            String direction = null;
            if (x < EDGE_SIZE && y < EDGE_SIZE) direction = "nw";
            else if (x > width - EDGE_SIZE && y < EDGE_SIZE) direction = "ne";
            else if (x < EDGE_SIZE && y > height - EDGE_SIZE) direction = "sw";
            else if (x > width - EDGE_SIZE && y > height - EDGE_SIZE) direction = "se";
            else if (x < EDGE_SIZE) direction = "w";
            else if (x > width - EDGE_SIZE) direction = "e";
            else if (y < EDGE_SIZE) direction = "n";
            else if (y > height - EDGE_SIZE) direction = "s";
            resizeDirection[0] = direction;
            scene.setCursor(getCursorForDirection(direction));
        });

        scene.setOnMousePressed(event -> {
            if (resizeDirection[0] != null && !isMaximized) {
                startX[0] = event.getScreenX();
                startY[0] = event.getScreenY();
                initialWidth[0] = primaryStage.getWidth();
                initialHeight[0] = primaryStage.getHeight();
                initialX[0] = primaryStage.getX();
                initialY[0] = primaryStage.getY();
                event.consume();
            }
        });

        scene.setOnMouseDragged(event -> {
            if (resizeDirection[0] != null && !isMaximized) {
                double deltaX = event.getScreenX() - startX[0];
                double deltaY = event.getScreenY() - startY[0];
                String dir = resizeDirection[0];
                double newWidth = initialWidth[0];
                double newHeight = initialHeight[0];
                double newX = initialX[0];
                double newY = initialY[0];
                if (dir.contains("e")) newWidth = Math.max(600, initialWidth[0] + deltaX);
                if (dir.contains("s")) newHeight = Math.max(400, initialHeight[0] + deltaY);
                if (dir.contains("w")) {
                    double potentialWidth = initialWidth[0] - deltaX;
                    if (potentialWidth >= 600) {
                        newWidth = potentialWidth;
                        newX = initialX[0] + deltaX;
                    }
                }
                if (dir.contains("n")) {
                    double potentialHeight = initialHeight[0] - deltaY;
                    if (potentialHeight >= 400) {
                        newHeight = potentialHeight;
                        newY = initialY[0] + deltaY;
                    }
                }
                primaryStage.setX(newX);
                primaryStage.setY(newY);
                primaryStage.setWidth(newWidth);
                primaryStage.setHeight(newHeight);
                event.consume();
            }
        });
        scene.setOnMouseReleased(event -> {
            if (resizeDirection[0] != null) {
                resizeDirection[0] = null;
                scene.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });
    }

    private javafx.scene.Cursor getCursorForDirection(String direction) {
        if (direction == null) return javafx.scene.Cursor.DEFAULT;
        return switch (direction) {
            case "n" -> javafx.scene.Cursor.N_RESIZE;
            case "s" -> javafx.scene.Cursor.S_RESIZE;
            case "e" -> javafx.scene.Cursor.E_RESIZE;
            case "w" -> javafx.scene.Cursor.W_RESIZE;
            case "ne" -> javafx.scene.Cursor.NE_RESIZE;
            case "nw" -> javafx.scene.Cursor.NW_RESIZE;
            case "se" -> javafx.scene.Cursor.SE_RESIZE;
            case "sw" -> javafx.scene.Cursor.SW_RESIZE;
            default -> javafx.scene.Cursor.DEFAULT;
        };
    }

    // 请将 createCustomTitleBar, createTitleBarButton, toggleMaximize, createMainLayout,
    // createTitledGroup, createConnectionGroup, createAdvancedSettingsGroup, createLabeledField
    // 等方法保持原样粘贴在这里...
    // (为了代码完整性，我这里只写出结构，实际使用时请保留你的原代码)

    private Region createCustomTitleBar() {
        // [保持你原本的代码不变]
        HBox titleBar = new HBox();
        titleBar.setPrefHeight(36);
        titleBar.getStyleClass().add("title-bar");
        ImageView logoView = new ImageView();
        try {
            Image logo = new Image(Objects.requireNonNull(MainWindowController.class.getResourceAsStream("/logo.png")));
            logoView.setImage(logo);
            logoView.setFitHeight(26);
            logoView.setPreserveRatio(true);
            HBox.setMargin(logoView, new Insets(0, 10, 0, 10));
        } catch (Exception ignored) {
        }
        Label titleLabel = new Label("NeoLink - 内网穿透客户端");
        titleLabel.getStyleClass().add("title-text");
        Button minButton = createTitleBarButton("⏷");
        minButton.setOnAction(e -> primaryStage.setIconified(true));
        Button maxButton = createTitleBarButton("⛶");
        maxButton.setOnAction(e -> toggleMaximize());
        Button closeButton = createTitleBarButton("✕");
        closeButton.getStyleClass().add("close-button");
        closeButton.setOnAction(e -> handleExit());
        HBox controls = new HBox(0, minButton, maxButton, closeButton);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(logoView, titleLabel, spacer, controls);
        titleBar.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                xOffset = event.getScreenX() - primaryStage.getX();
                yOffset = event.getScreenY() - primaryStage.getY();
            }
        });
        titleBar.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isMaximized) {
                primaryStage.setX(event.getScreenX() - xOffset);
                primaryStage.setY(event.getScreenY() - yOffset);
            }
        });
        titleBar.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximize();
            }
        });
        return titleBar;
    }

    private Button createTitleBarButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("title-bar-button");
        button.setFocusTraversable(false);
        return button;
    }

    private void toggleMaximize() {
        if (isMaximized) {
            primaryStage.setX(NeoLink.savedWindowX);
            primaryStage.setY(NeoLink.savedWindowY);
            primaryStage.setWidth(NeoLink.savedWindowWidth);
            primaryStage.setHeight(NeoLink.savedWindowHeight);
            isMaximized = false;
        } else {
            NeoLink.savedWindowX = primaryStage.getX();
            NeoLink.savedWindowY = primaryStage.getY();
            NeoLink.savedWindowWidth = primaryStage.getWidth();
            NeoLink.savedWindowHeight = primaryStage.getHeight();
            Screen screen = Screen.getPrimary();
            Rectangle2D bounds = screen.getVisualBounds();
            primaryStage.setX(bounds.getMinX());
            primaryStage.setY(bounds.getMinY());
            primaryStage.setWidth(bounds.getWidth());
            primaryStage.setHeight(bounds.getHeight());
            isMaximized = true;
        }
    }

    private BorderPane createMainLayout() {
        VBox root = new VBox();
        root.getChildren().add(createCustomTitleBar());
        BorderPane contentPane = new BorderPane();
        contentPane.setPadding(new Insets(24));
        VBox topSection = new VBox(24);
        topSection.getChildren().addAll(createConnectionGroup(), createAdvancedSettingsGroup());
        contentPane.setTop(topSection);
        VBox centerSection = createLogSection();
        contentPane.setCenter(centerSection);
        HBox bottomBar = createBottomBar();
        contentPane.setBottom(bottomBar);
        BorderPane.setMargin(topSection, new Insets(0, 0, 24, 0));
        root.getChildren().add(contentPane);
        VBox.setVgrow(contentPane, Priority.ALWAYS);
        return new BorderPane(root);
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

    private VBox createAdvancedSettingsGroup() {
        TitledPane advancedSettingsPane = new TitledPane();
        advancedSettingsPane.setText("高级设置");
        advancedSettingsPane.setExpanded(false);
        advancedSettingsPane.getStyleClass().add("titled-pane");
        GridPane advancedGrid = new GridPane();
        advancedGrid.setHgap(15);
        advancedGrid.setVgap(15);
        advancedGrid.setPadding(new Insets(15));
        Label localDomainLabel = new Label("本地域名:");
        localDomainField = new TextField();
        localDomainField.setPromptText("本地域名 (默认: localhost)");
        localDomainField.setText(NeoLink.localDomainName);
        localDomainField.setPrefWidth(200);
        Label hostHookPortLabel = new Label("服务端口:");
        hostHookPortField = new TextField();
        hostHookPortField.setPromptText("服务端口 (默认: 44801)");
        hostHookPortField.setText(String.valueOf(NeoLink.hostHookPort));
        hostHookPortField.setPrefWidth(200);
        Label hostConnectPortLabel = new Label("连接端口:");
        hostConnectPortField = new TextField();
        hostConnectPortField.setPromptText("连接端口 (默认: 44802)");
        hostConnectPortField.setText(String.valueOf(NeoLink.hostConnectPort));
        hostConnectPortField.setPrefWidth(200);
        Label protocolLabel = new Label("协议启用:");
        HBox protocolBox = new HBox(15);
        HBox tcpBox = createCustomCheckBox("启用TCP", !NeoLink.isDisableTCP);
        HBox udpBox = createCustomCheckBox("启用UDP", !NeoLink.isDisableUDP);
        protocolBox.getChildren().addAll(tcpBox, udpBox);
        Label reconnectLabel = new Label("自动重连:");
        HBox reconnectBox = createCustomCheckBox("启用自动重连", NeoLink.enableAutoReconnect);
        advancedGrid.add(localDomainLabel, 0, 0);
        advancedGrid.add(localDomainField, 1, 0);
        advancedGrid.add(hostHookPortLabel, 0, 1);
        advancedGrid.add(hostHookPortField, 1, 1);
        advancedGrid.add(hostConnectPortLabel, 0, 2);
        advancedGrid.add(hostConnectPortField, 1, 2);
        advancedGrid.add(protocolLabel, 0, 3);
        advancedGrid.add(protocolBox, 1, 3);
        advancedGrid.add(reconnectLabel, 0, 4);
        advancedGrid.add(reconnectBox, 1, 4);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPrefWidth(100);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPrefWidth(300);
        advancedGrid.getColumnConstraints().addAll(col1, col2);
        advancedSettingsPane.setContent(advancedGrid);
        VBox group = new VBox(5);
        group.getChildren().add(advancedSettingsPane);
        return group;
    }

    private HBox createLabeledField(String labelText, Control field) {
        Label label = new Label(labelText);
        HBox box = new HBox(8, label, field);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox createLogSection() {
        Label logTitle = new Label("运行日志");
        logTitle.getStyleClass().add("log-title");

        logWebView = new WebView();
        webEngine = logWebView.getEngine(); // 缓存 engine
        logWebView.setContextMenuEnabled(false);

        // --- ContextMenu 逻辑 (保持不变) ---
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> {
            String script = "window.getSelection().toString();";
            Object result = webEngine.executeScript(script);
            if (result instanceof String selectedText && !selectedText.isEmpty()) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(selectedText);
                clipboard.setContent(content);
            }
            contextMenu.hide();
        });
        contextMenu.getItems().add(copyItem);

        logWebView.setOnContextMenuRequested(e -> {
            String script = "window.getSelection().toString();";
            Object result = webEngine.executeScript(script);
            boolean hasSelection = (result instanceof String && !((String) result).isEmpty());
            copyItem.setDisable(!hasSelection);
            contextMenu.show(logWebView, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        logWebView.setOnMousePressed(e -> {
            if ((e.getButton() == javafx.scene.input.MouseButton.PRIMARY ||
                    e.getButton() == javafx.scene.input.MouseButton.MIDDLE) &&
                    contextMenu.isShowing()) {
                contextMenu.hide();
            }
        });

        // 拖拽处理
        logWebView.setOnDragOver(Event::consume);
        logWebView.setOnDragEntered(Event::consume);
        logWebView.setOnDragExited(Event::consume);
        logWebView.setOnDragDropped(event -> {
            event.setDropCompleted(false);
            event.consume();
        });

        // --- 修复点：使用占位符替换，避免 CSS 中的 % 符号引发 String.format 异常 ---
        String initialHtmlTemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        /* 基本样式保持不变 */
                        html, body {
                            background-color: #0c0c0c;
                            color: #cccccc;
                            font-family: 'Consolas', 'Courier New', monospace;
                            font-size: 13px;
                            margin: 0;
                            padding: 0;
                            height: 100%; /* 这里的 % 不会再报错了 */
                            overflow: hidden;
                        }
                
                        #scroll-container {
                            height: 100vh;
                            overflow-y: auto;
                            overflow-x: hidden;
                            padding: 12px;
                            box-sizing: border-box;
                            white-space: pre-wrap;
                            word-wrap: break-word;
                            margin-right: -17px;
                            padding-right: 17px;
                        }
                
                        /* 你的滚动条隐藏 CSS */
                        ::-webkit-scrollbar { display: none !important; width: 0px; height: 0px; visibility: hidden; opacity: 0; }
                        * { scrollbar-width: none !important; -ms-overflow-style: none !important; }
                    </style>
                    <script>
                        // 核心优化：批量追加日志并在 JS 端截断 DOM
                        function appendLogBatch(htmlBatch) {
                            var container = document.getElementById('scroll-container');
                            if (!container) return;
                
                            // 创建临时容器解析 HTML
                            var tempDiv = document.createElement('div');
                            tempDiv.innerHTML = htmlBatch;
                
                            // 将新节点移动到主容器
                            while (tempDiv.firstChild) {
                                container.appendChild(tempDiv.firstChild);
                            }
                
                            // 保持最多 MAX_ENTRIES_PLACEHOLDER 条
                            var maxEntries = MAX_ENTRIES_PLACEHOLDER;
                            var totalNodes = container.children.length;
                            if (totalNodes > maxEntries) {
                                for (var i = 0; i < (totalNodes - maxEntries); i++) {
                                    if (container.firstChild) {
                                        container.removeChild(container.firstChild);
                                    }
                                }
                            }
                
                            // 滚动到底部
                            container.scrollTop = container.scrollHeight;
                        }
                    </script>
                </head>
                <body>
                    <div id="scroll-container"></div>
                </body>
                </html>""";

        // 使用 replace 替换占位符，这是处理包含 CSS 代码的模板最安全的方法
        String initialHtml = initialHtmlTemplate.replace("MAX_ENTRIES_PLACEHOLDER", String.valueOf(MAX_LOG_ENTRIES));

        webEngine.loadContent(initialHtml);

        // 页面加载完成后移除滚动条的逻辑保持不变
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == javafx.concurrent.Worker.State.SUCCEEDED) {
                webEngine.executeScript(
                        "var style = document.createElement('style');" +
                                "style.innerHTML = `*::-webkit-scrollbar { display: none !important; width: 0px; height: 0px; }`;" +
                                "document.head.appendChild(style);"
                );
            }
        });

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

    // ================== 消费者线程优化 ==================
    private void startLogConsumer() {
        logConsumerExecutor = Executors.newSingleThreadExecutor();
        logConsumerExecutor.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 1. 阻塞等待新日志
                    String message = LogMessageQueue.take();

                    // 2. 预处理 HTML (在后台线程完成，减轻 UI 线程负担)
                    String logHtml = buildLogEntryHtml(message);

                    // 3. 加入 UI 缓冲队列
                    pendingLogBuffer.offer(logHtml);

                    // 4. 请求 UI 更新 (节流)
                    requestUiUpdate();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // 将单条日志预处理为 HTML 字符串
    private String buildLogEntryHtml(String ansiText) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='white-space: pre-wrap; word-wrap: break-word;'>");
        if (ansiText.contains("\033[")) {
            sb.append(parseAnsiToHtml(ansiText));
        } else {
            sb.append(escapeJsString(ansiText));
        }
        sb.append("</div>");
        return sb.toString();
    }

    // 请求在 JavaFX 线程执行更新
    private void requestUiUpdate() {
        // 如果已经有更新任务在排队，就不再提交，防止 flood
        if (isUpdatePending.compareAndSet(false, true)) {
            Platform.runLater(this::processLogQueue);
        }
    }

    // 实际执行更新的方法 (运行在 JavaFX Application Thread)
    private void processLogQueue() {
        try {
            if (pendingLogBuffer.isEmpty()) return;

            StringBuilder batchHtml = new StringBuilder();
            String htmlFragment;

            // 一次性取出所有积压的日志，拼成一个大字符串
            while ((htmlFragment = pendingLogBuffer.poll()) != null) {
                batchHtml.append(htmlFragment);
            }

            if (webEngine != null && webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                // 调用 JS 函数进行批量添加
                // 注意：这里需要再次转义 batchHtml 因为它现在是 JS 函数的参数
                // 但由于我们拼接的是 HTML，我们可以用特定方式传递，或者简单转义
                // 为了保险，我们将 HTML 内容转义为 JS 字符串
                String jsCode = "appendLogBatch(`" + batchHtml.toString().replace("`", "\\`").replace("$", "\\$") + "`);";
                webEngine.executeScript(jsCode);
            }

            // 更新完后尝试隐藏滚动条
            hideWebViewScrollBars();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 释放锁
            isUpdatePending.set(false);

            // 双重检查：如果在处理期间又来了新日志，再次调度
            if (!pendingLogBuffer.isEmpty()) {
                requestUiUpdate();
            }
        }
    }

    // ========================================================

    // 不再需要旧的 appendLogToWebView 方法，已被 requestUiUpdate 和 processLogQueue 替代

    private void startService() {
        if (isRunning) return;
        if (!validateForm()) return;
        resetNeoLinkState();
        NeoLink.remoteDomainName = remoteDomainField.getText().trim();
        NeoLink.localPort = Integer.parseInt(localPortField.getText().trim());
        NeoLink.key = accessKeyField.getText();
        applyAdvancedSettings();
        languageData = languageData.flush();
        NeoLink.say("正在启动 NeoLink 服务...");
        NeoLink.printBasicInfo();
        isRunning = true;
        updateButtonState();
        currentTask = coreExecutor.submit(() -> {
            try {
                NeoLinkCoreRunner.runCore(NeoLink.remoteDomainName, NeoLink.localPort, NeoLink.key);
            } finally {
                resetState();
            }
        });
    }

    private void resetNeoLinkState() {
        NeoLink.hookSocket = null;
        NeoLink.remotePort = 0;
        NeoLink.isReconnectedOperation = false;
        NeoLink.inputScanner = new Scanner(new ByteArrayInputStream(new byte[0]));
        ConfigOperator.readAndSetValue();
    }

    public void stopService() {
        NeoLink.say("正在关闭 NeoLink 服务...");
        if (!isRunning) return;
        NeoLinkCoreRunner.requestStop();
        if (NeoLink.hookSocket != null) {
            try {
                NeoLink.hookSocket.close();
            } catch (Exception ignored) {
            }
        }
        CheckAliveThread.stopThread();
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
        // [保持原样]
        StringBuilder errors = new StringBuilder();
        if (remoteDomainField.getText().trim().isEmpty()) {
            errors.append("• 请输入远程服务器地址。 \n");
        }
        String portStr = localPortField.getText().trim();
        if (portStr.isEmpty()) {
            errors.append("• 请输入本地服务端口。 ");
        } else if (!PORT_PATTERN.matcher(portStr).matches()) {
            errors.append("• 本地端口必须是1-65535之间的数字。 \n");
        } else {
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                errors.append("• 本地端口必须是1-65535之间的数字。 \n");
            }
        }
        if (accessKeyField.getText().trim().isEmpty()) {
            errors.append("• 请输入访问密钥。 ");
        }
        String hookPortStr = hostHookPortField.getText().trim();
        if (!hookPortStr.isEmpty()) {
            if (!PORT_PATTERN.matcher(hookPortStr).matches()) {
                errors.append("• 服务端口必须是1-65535之间的数字。 \n");
            } else {
                int port = Integer.parseInt(hookPortStr);
                if (port < 1 || port > 65535) {
                    errors.append("• 服务端口必须是1-65535之间的数字。 \n");
                }
            }
        }
        String connectPortStr = hostConnectPortField.getText().trim();
        if (!connectPortStr.isEmpty()) {
            if (!PORT_PATTERN.matcher(connectPortStr).matches()) {
                errors.append("• 连接端口必须是1-65535之间的数字。 \n");
            } else {
                int port = Integer.parseInt(connectPortStr);
                if (port < 1 || port > 65535) {
                    errors.append("• 连接端口必须是1-65535之间的数字。 \n");
                }
            }
        }
        if (!errors.isEmpty()) {
            showAlert(errors.toString().trim());
            return false;
        }
        return true;
    }

    private String escapeJsString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("'", "\\'")  // 额外增加单引号转义
                .replace("\"", "\\\""); // 额外增加双引号转义
    }

    private String parseAnsiToHtml(String ansiText) {
        if (ansiText == null) return "";
        String html = escapeJsString(ansiText);
        html = html.replaceAll("\033\\[31m", "<span style='color: #ff5555;'>");
        html = html.replaceAll("\033\\[32m", "<span style='color: #50fa7b;'>");
        html = html.replaceAll("\033\\[33m", "<span style='color: #f1fa8c;'>");
        html = html.replaceAll("\033\\[34m", "<span style='color: #bd93f9;'>");
        html = html.replaceAll("\033\\[35m", "<span style='color: #ff79c6;'>");
        html = html.replaceAll("\033\\[36m", "<span style='color: #8be9fd;'>");
        html = html.replaceAll("\033\\[0m", "</span>");
        return html;
    }

    private void showAlert(String message) {
        // [保持原样]
        Platform.runLater(() -> {
            try {
                Stage dialogStage = new Stage();
                dialogStage.initStyle(StageStyle.UNDECORATED);
                dialogStage.initOwner(primaryStage);
                dialogStage.initModality(Modality.APPLICATION_MODAL);
                dialogStage.setResizable(false);
                VBox root = new VBox();
                root.getChildren().add(createCustomTitleBarForDialog(dialogStage));
                BorderPane contentPane = new BorderPane();
                contentPane.setPadding(new Insets(24));
                VBox centerSection = new VBox(24);
                centerSection.setAlignment(Pos.CENTER);
                Label warningIcon = new Label("⚠");
                warningIcon.setStyle("-fx-font-size: 48px; -fx-text-fill: #ff9800; -fx-font-weight: bold;");
                Label messageLabel = new Label(message);
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(450);
                messageLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-text-alignment: center;");
                messageLabel.setAlignment(Pos.CENTER);
                Button okButton = new Button("确定");
                okButton.getStyleClass().add("primary-button");
                okButton.setPrefWidth(120);
                okButton.setPrefHeight(36);
                okButton.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
                okButton.setOnAction(e -> dialogStage.close());
                centerSection.getChildren().addAll(warningIcon, messageLabel, okButton);
                contentPane.setCenter(centerSection);
                root.getChildren().add(contentPane);
                VBox.setVgrow(contentPane, Priority.ALWAYS);
                Scene scene = new Scene(new BorderPane(root), 500, 250);
                String css = Objects.requireNonNull(MainWindowController.class.getResource("/dark-theme-webview.css")).toExternalForm();
                scene.getStylesheets().add(css);
                String contextMenuCss = Objects.requireNonNull(MainWindowController.class.getResource("/dark-context-menu.css")).toExternalForm();
                scene.getStylesheets().add(contextMenuCss);
                dialogStage.setScene(scene);
                dialogStage.setOnShown(event -> {
                    dialogStage.setX(primaryStage.getX() + primaryStage.getWidth() / 2 - dialogStage.getWidth() / 2);
                    dialogStage.setY(primaryStage.getY() + primaryStage.getHeight() / 2 - dialogStage.getHeight() / 2);
                });
                dialogStage.showAndWait();
            } catch (Exception e) {
                System.err.println("Failed to create custom dialog: " + e.getMessage());
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initOwner(primaryStage);
                alert.initModality(Modality.APPLICATION_MODAL);
                alert.setTitle("输入错误");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            }
        });
    }

    private Region createCustomTitleBarForDialog(Stage dialogStage) {
        // [保持原样]
        HBox titleBar = new HBox();
        titleBar.setPrefHeight(36);
        titleBar.getStyleClass().add("title-bar");
        ImageView logoView = new ImageView();
        try {
            Image logo = new Image(Objects.requireNonNull(MainWindowController.class.getResourceAsStream("/logo.png")));
            logoView.setImage(logo);
            logoView.setFitHeight(26);
            logoView.setPreserveRatio(true);
            HBox.setMargin(logoView, new Insets(0, 10, 0, 10));
        } catch (Exception ignored) {
        }
        Label titleLabel = new Label("输入错误");
        titleLabel.getStyleClass().add("title-text");
        Button closeButton = createTitleBarButton("✕");
        closeButton.getStyleClass().add("close-button");
        closeButton.setOnAction(e -> dialogStage.close());
        HBox controls = new HBox(0, closeButton);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(logoView, titleLabel, spacer, controls);
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        titleBar.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                xOffset[0] = event.getScreenX() - dialogStage.getX();
                yOffset[0] = event.getScreenY() - dialogStage.getY();
            }
        });
        titleBar.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                dialogStage.setX(event.getScreenX() - xOffset[0]);
                dialogStage.setY(event.getScreenY() - yOffset[0]);
            }
        });
        return titleBar;
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

    private HBox createCustomCheckBox(String text, boolean selected) {
        // [保持原样]
        StackPane checkBox = new StackPane();
        checkBox.setMinSize(18, 18);
        checkBox.setMaxSize(18, 18);
        checkBox.setPrefSize(18, 18);
        if (selected) {
            checkBox.setStyle("-fx-background-color: #0078d4; -fx-border-color: #0078d4; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        } else {
            checkBox.setStyle("-fx-background-color: #202020; -fx-border-color: #555555; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        }
        Label checkMark = new Label("✔");
        checkMark.setStyle("-fx-font-family: 'Segoe UI Symbol', 'Arial', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        checkMark.setVisible(selected);
        if (text.contains("TCP")) {
            tcpCheckMark = checkMark;
        } else if (text.contains("UDP")) {
            udpCheckMark = checkMark;
        } else if (text.contains("自动重连")) {
            reconnectCheckMark = checkMark;
        }
        checkBox.getChildren().add(checkMark);
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px;");
        HBox box = new HBox(8, checkBox, label);
        box.setAlignment(Pos.CENTER_LEFT);
        final long[] lastClickTime = {0};
        final boolean isTcpOrUdp = text.contains("TCP") || text.contains("UDP");
        final long cooldownPeriod = 500;
        box.setOnMouseClicked(e -> {
            if (isTcpOrUdp) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime[0] < cooldownPeriod) {
                    return;
                }
                lastClickTime[0] = currentTime;
            }
            boolean newState = !checkMark.isVisible();
            checkMark.setVisible(newState);
            if (newState) {
                checkBox.setStyle("-fx-background-color: #0078d4; -fx-border-color: #0078d4; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            } else {
                checkBox.setStyle("-fx-background-color: #202020; -fx-border-color: #555555; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
            if (text.contains("TCP")) {
                NeoLink.isDisableTCP = !newState;
                sendTCPandUDPState();
                NeoLink.say("TCP协议已" + (newState ? "启用" : "禁用"));
            } else if (text.contains("UDP")) {
                NeoLink.isDisableUDP = !newState;
                sendTCPandUDPState();
                NeoLink.say("UDP协议已" + (newState ? "启用" : "禁用"));
            } else if (text.contains("自动重连")) {
                NeoLink.enableAutoReconnect = newState;
                NeoLink.say("自动重连已" + (newState ? "启用" : "禁用"));
            }
        });
        box.setOnMouseEntered(e -> {
            if (!checkMark.isVisible()) {
                checkBox.setStyle("-fx-background-color: #202020; -fx-border-color: #777777; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
        });
        box.setOnMouseExited(e -> {
            if (!checkMark.isVisible()) {
                checkBox.setStyle("-fx-background-color: #202020; -fx-border-color: #555555; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
        });
        return box;
    }

    private void sendTCPandUDPState() {
        // [保持原样]
        String command = "";
        if (!isDisableTCP) command = command.concat("T");
        if (!isDisableUDP) command = command.concat("U");
        try {
            if (isRunning && hookSocket != null) {
                sendStr(command);
            }
        } catch (IOException e) {
            debugOperation(e);
        }
    }

    private void applyAdvancedSettings() {
        // [保持原样]
        String localDomain = localDomainField.getText().trim();
        if (!localDomain.isEmpty()) NeoLink.localDomainName = localDomain;
        String hookPortStr = hostHookPortField.getText().trim();
        if (!hookPortStr.isEmpty() && PORT_PATTERN.matcher(hookPortStr).matches()) {
            int hookPort = Integer.parseInt(hookPortStr);
            if (hookPort > 0 && hookPort <= 65535) NeoLink.hostHookPort = hookPort;
        }
        String connectPortStr = hostConnectPortField.getText().trim();
        if (!connectPortStr.isEmpty() && PORT_PATTERN.matcher(connectPortStr).matches()) {
            int connectPort = Integer.parseInt(connectPortStr);
            if (connectPort > 0 && connectPort <= 65535) NeoLink.hostConnectPort = connectPort;
        }
        boolean tcpEnabled = (tcpCheckMark != null && tcpCheckMark.isVisible());
        boolean udpEnabled = (udpCheckMark != null && udpCheckMark.isVisible());
        boolean autoReconnectEnabled = (reconnectCheckMark != null && reconnectCheckMark.isVisible());
        NeoLink.isDisableTCP = !tcpEnabled;
        NeoLink.isDisableUDP = !udpEnabled;
        NeoLink.enableAutoReconnect = autoReconnectEnabled;
    }
}