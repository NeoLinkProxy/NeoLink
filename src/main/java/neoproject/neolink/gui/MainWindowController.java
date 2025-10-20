package neoproject.neolink.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import neoproject.neolink.ConfigOperator;
import neoproject.neolink.NeoLink;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import plethora.print.log.Loggist;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoLink GUI 主窗口控制器 (最终修正版)
 * - 标题栏高度 32px，视觉宽松
 * - Logo 高度 22px（比文字大）
 * - 控制按钮严格靠右
 * - 修正最小化图标为 \uE949
 * - 非 Windows 使用 "－", "□", "✕"
 * - 增加自动启动支持
 * - 修复字符串字面量问题
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
    private boolean isMaximized = false;
    private double xOffset = 0;
    private double yOffset = 0;
    private static boolean shouldAutoStart = false; // 静态标志位
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");
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

    // 设置自动启动标志的静态方法
    public static void setAutoStart(boolean autoStart) {
        shouldAutoStart = autoStart;
    }

    public void show() {
        // --- 关键修改：首先调用 NeoLink.initializeLogger() 来设置 fileLoggist ---
        // 这会根据 NeoLink.outputFilePath (已由 parseCommandLineArgs 设置) 创建正确的日志文件写入器
        // 并将其赋值给 NeoLink.loggist。我们需要先保存它。
        try {
            // 调用 NeoLink 的初始化方法，这会创建一个带有文件写入功能的 Loggist 实例
            // 并将其赋值给 NeoLink.loggist
            NeoLink.initializeLogger();
        } catch (Exception e) {
            // 如果初始化失败，记录错误并可能需要禁用文件日志功能
            System.err.println("Failed to initialize NeoLink logger (file writer): " + e.getMessage());
            e.printStackTrace();
            // 可以考虑弹窗提示用户或记录到一个备用日志
            // 这里简单打印到控制台，实际应用中可能需要更优雅的处理
        }

        // 此时，NeoLink.loggist 是一个具有文件写入能力的 Loggist 实例 (我们称之为 fileLoggist)
        // 保存这个实例的引用，以便 QueueBasedLoggist 可以使用它来写入文件
        Loggist fileLoggist = NeoLink.loggist;

        // --- 然后设置 GUI 日志重定向器 ---
        // GuiLogRedirector 会将 System.out/err 重定向到 LogMessageQueue
        new GuiLogRedirector(LogMessageQueue::offer);

        // --- 最后，创建 QueueBasedLoggist 并赋值给 NeoLink.loggist ---
        // 这个实例会将日志发送到队列（供 GUI 显示），并委托给 fileLoggist 写入文件
        NeoLink.loggist = new QueueBasedLoggist(fileLoggist);

        // --- 继续执行其他初始化逻辑 ---
        NeoLink.detectLanguage();
        NeoLink.inputScanner = new Scanner(new ByteArrayInputStream(new byte[0]));
        ConfigOperator.readAndSetValue();
        NeoLink.printLogo(); // 这个调用的日志现在会写入指定文件（如果 --output-file 被使用）和 GUI
        NeoLink.printBasicInfo(); // 这个调用的日志现在会写入指定文件（如果 --output-file 被使用）和 GUI

        primaryStage.initStyle(StageStyle.UNDECORATED);
        Scene scene = new Scene(createMainLayout(), 950, 700);
        String css = Objects.requireNonNull(MainWindowController.class.getResource("/dark-theme-webview.css")).toExternalForm();
        scene.getStylesheets().add(css);

        // --- 添加拖放事件处理，防止外部拖拽导致 NoClassDefFoundError ---
        // 为 Scene 添加事件处理，可以捕获窗口区域内的拖拽事件
        scene.setOnDragOver(event -> {
            // 消费事件，阻止默认处理，防止错误
            event.consume();
        });
        // 可选：处理拖拽进入
        scene.setOnDragEntered(event -> {
            // 可以在这里改变视觉反馈，但同样要消费事件
            event.consume();
        });
        // 可选：处理拖拽离开
        scene.setOnDragExited(event -> {
            // 恢复视觉反馈，消费事件
            event.consume();
        });
        // 可选：处理拖拽放置（虽然我们不希望发生，但也要消费）
        scene.setOnDragDropped(event -> {
            // 消费事件，不处理放置
            event.setDropCompleted(false);
            event.consume();
        });

        try {
            Image appIcon = new Image(Objects.requireNonNull(
                    MainWindowController.class.getResourceAsStream("/logo.png") // 修改文件名为 logo.ico
            ));
            primaryStage.getIcons().add(appIcon);
        } catch (Exception e) {
            // 如果加载 ICO 失败，记录错误（可选）或忽略
            System.err.println("Warning: Could not load logo.png: " + e.getMessage());
            // e.printStackTrace(); // 可选：打印堆栈跟踪
            // 如果 ICO 加载失败，可以尝试加载 PNG（如果之前有）
            // try {
            //     Image appIconPng = new Image(Objects.requireNonNull(
            //         MainWindowController.class.getResourceAsStream("/logo.png")
            //     ));
            //     primaryStage.getIcons().add(appIconPng);
            // } catch (Exception ignored) {
            //     // 如果 PNG 也加载失败，忽略
            // }
        }
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> handleExit());
        primaryStage.show();
        startLogConsumer();
        setupWindowResizeHandlers(scene);

        // 检查是否需要自动启动
        if (shouldAutoStart) {
            // 使用 Platform.runLater 将启动操作调度到 JavaFX 应用线程
            Platform.runLater(() -> {
                // 预填充字段，如果需要的话 (通常参数解析已经设置好 NeoLink.key 和 NeoLink.localPort)
                if (NeoLink.key != null) {
                    accessKeyField.setText(NeoLink.key);
                }
                if (NeoLink.localPort != -1) {
                    localPortField.setText(String.valueOf(NeoLink.localPort));
                }
                // 调用 startService 方法
                startService();
            });
        }
    }

    private void setupWindowResizeHandlers(Scene scene) {
        final double[] startX = new double[1];
        final double[] startY = new double[1];
        final double[] initialWidth = new double[1];
        final double[] initialHeight = new double[1];
        final double[] initialX = new double[1];
        final double[] initialY = new double[1];

        // 定义边缘检测的宽度
        final double EDGE_SIZE = 5;

        // 存储当前调整状态 (null, "n", "s", "e", "w", "ne", "nw", "se", "sw")
        final String[] resizeDirection = {null};

        scene.setOnMouseMoved(event -> {
            if (isMaximized) return; // 最大化时禁用

            double x = event.getX();
            double y = event.getY();
            double width = primaryStage.getWidth();
            double height = primaryStage.getHeight();

            // 根据鼠标位置确定光标和调整方向
            String direction = null;

            if (x < EDGE_SIZE && y < EDGE_SIZE) {
                direction = "nw";
            } else if (x > width - EDGE_SIZE && y < EDGE_SIZE) {
                direction = "ne";
            } else if (x < EDGE_SIZE && y > height - EDGE_SIZE) {
                direction = "sw";
            } else if (x > width - EDGE_SIZE && y > height - EDGE_SIZE) {
                direction = "se";
            } else if (x < EDGE_SIZE) {
                direction = "w";
            } else if (x > width - EDGE_SIZE) {
                direction = "e";
            } else if (y < EDGE_SIZE) {
                direction = "n";
            } else if (y > height - EDGE_SIZE) {
                direction = "s";
            }

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
                event.consume(); // 防止事件传递到其他组件
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

                // 根据调整方向计算新尺寸和位置
                if (dir.contains("e")) {
                    newWidth = Math.max(600, initialWidth[0] + deltaX); // 最小宽度
                }
                if (dir.contains("s")) {
                    newHeight = Math.max(400, initialHeight[0] + deltaY); // 最小高度
                }
                if (dir.contains("w")) {
                    double potentialWidth = initialWidth[0] - deltaX;
                    if (potentialWidth >= 600) { // 最小宽度检查
                        newWidth = potentialWidth;
                        newX = initialX[0] + deltaX;
                    }
                }
                if (dir.contains("n")) {
                    double potentialHeight = initialHeight[0] - deltaY;
                    if (potentialHeight >= 400) { // 最小高度检查
                        newHeight = potentialHeight;
                        newY = initialY[0] + deltaY;
                    }
                }

                primaryStage.setX(newX);
                primaryStage.setY(newY);
                primaryStage.setWidth(newWidth);
                primaryStage.setHeight(newHeight);
                event.consume(); // 防止事件传递到其他组件
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
        if (direction == null) {
            return javafx.scene.Cursor.DEFAULT;
        }
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

    private Region createCustomTitleBar() {
        HBox titleBar = new HBox();
        titleBar.setPrefHeight(36); // ✅ 增高到 36px
        titleBar.getStyleClass().add("title-bar");
        // Logo: 26px 高
        ImageView logoView = new ImageView();
        try {
            Image logo = new Image(Objects.requireNonNull(MainWindowController.class.getResourceAsStream("/logo.png")));
            logoView.setImage(logo);
            logoView.setFitHeight(26); // ✅ 明显更大
            logoView.setPreserveRatio(true);
            HBox.setMargin(logoView, new Insets(0, 10, 0, 10));
        } catch (Exception ignored) {
        }
        Label titleLabel = new Label("NeoLink - 内网穿透客户端");
        titleLabel.getStyleClass().add("title-text");
        // ✅ 使用广泛支持的 Unicode 符号（不再依赖 Segoe MDL2 Assets）
        String minText = "⏷";  // U+23F7: downwards arrow
        String maxText = "⛶";  // U+26F6: square with diagonal crosshatch
        String closeText = "✕"; // U+2715: multiplication x
        Button minButton = createTitleBarButton(minText);
        minButton.setOnAction(e -> primaryStage.setIconified(true));
        Button maxButton = createTitleBarButton(maxText);
        maxButton.setOnAction(e -> toggleMaximize());
        Button closeButton = createTitleBarButton(closeText);
        closeButton.getStyleClass().add("close-button");
        closeButton.setOnAction(e -> handleExit());

        HBox controls = new HBox(0, minButton, maxButton, closeButton);
        // ✅ 关键：插入一个可伸缩的空白区域，把 controls 推到最右边
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // 布局: [logo][title][spacer][controls]
        titleBar.getChildren().addAll(logoView, titleLabel, spacer, controls);
        // 拖拽 & 双击逻辑（保持不变）
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
        topSection.getChildren().addAll(createConnectionGroup());
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

    // ========== 以下保持不变 ==========
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

    private VBox createLogSection() {
        Label logTitle = new Label("运行日志");
        logTitle.getStyleClass().add("log-title");

        logWebView = new WebView();
        logWebView.setContextMenuEnabled(false);
        // 修复：使用字符串拼接代替多行字符串字面量
        String initialHtml = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <style>\n" +
                "        body {\n" +
                "            background-color: #0c0c0c;\n" +
                "            color: #cccccc;\n" +
                "            font-family: 'Consolas', 'Courier New', monospace;\n" +
                "            font-size: 13px;\n" +
                "            margin: 0;\n" +
                "            padding: 12px;\n" +
                "            white-space: pre-wrap;\n" +
                "            word-wrap: break-word;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "</body>\n" +
                "</html>";
        logWebView.getEngine().loadContent(initialHtml);

        // --- 关键修复：为 WebView 控件添加 JavaFX 拖放事件处理 ---
        // 这些处理器会在事件到达 WebView 内部渲染引擎之前消费它们
        logWebView.setOnDragOver(event -> {
            // 消费 dragover 事件，阻止默认行为和进一步处理
            event.consume();
        });

        logWebView.setOnDragEntered(event -> {
            // 消费 dragentered 事件
            event.consume();
        });

        logWebView.setOnDragExited(event -> {
            // 消费 dragexited 事件
            event.consume();
        });

        logWebView.setOnDragDropped(event -> {
            // 消费 drop 事件，阻止默认行为和进一步处理
            // 设置 dropCompleted 为 false，明确表示未处理此操作
            event.setDropCompleted(false);
            event.consume();
        });
        // --- 修复结束 ---

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

    // ========== 原有逻辑保持不变 ==========
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
        NeoLink.inputScanner = new Scanner(new ByteArrayInputStream(new byte[0]));
    }

    private void stopService() {
        NeoLink.say("正在关闭 NeoLink 服务...");
        if (!isRunning) return;
        NeoLinkCoreRunner.requestStop();
        if (NeoLink.hookSocket != null) {
            try {
                NeoLink.hookSocket.close();
            } catch (Exception ignored) {
            }
        }
        neoproject.neolink.threads.CheckAliveThread.stopThread();
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
                            "document.body.addEventListener('dragover', function(e) { e.preventDefault(); e.dataTransfer.dropEffect = 'none'; }, false);" +
                            "document.body.addEventListener('drop', function(e) { e.preventDefault(); }, false);" +
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