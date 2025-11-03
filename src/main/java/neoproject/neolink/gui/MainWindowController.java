package neoproject.neolink.gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import neoproject.neolink.ConfigOperator;
import neoproject.neolink.NeoLink;
import neoproject.neolink.threads.CheckAliveThread;
import plethora.print.log.Loggist;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static neoproject.neolink.NeoLink.debugOperation;

/**
 * NeoLink GUI 主窗口控制器 (最终修正版)
 * - 标题栏高度 32px，视觉宽松
 * - Logo 高度 22px（比文字大）
 * - 控制按钮严格靠右
 * - 修正最小化图标为 \uE949
 * - 非 Windows 使用 "－", "□", "✕"
 * - 增加自动启动支持
 * - 修复字符串字面量问题
 * - 添加高级设置下拉框
 * - 使用ToggleButton替代CheckBox
 */
public class MainWindowController {
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");
    private static boolean shouldAutoStart = false; // 静态标志位

    private final Stage primaryStage;
    private final ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService logConsumerExecutor;
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

    private TextField localDomainField;
    private TextField hostHookPortField;
    private TextField hostConnectPortField;
    private Label tcpCheckMark;
    private Label udpCheckMark;
    private Label reconnectCheckMark;

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
            debugOperation(e);
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

        // --- 添加 ContextMenu 的 CSS 样式表 ---
        String contextMenuCss = Objects.requireNonNull(
                MainWindowController.class.getResource("/dark-context-menu.css")
        ).toExternalForm();
        scene.getStylesheets().add(contextMenuCss);
        // --- 添加结束 ---

        // --- 添加拖放事件处理，防止外部拖拽导致 NoClassDefFoundError ---
        // 为 Scene 添加事件处理，可以捕获窗口区域内的拖拽事件
        // 消费事件，阻止默认处理，防止错误
        scene.setOnDragOver(Event::consume);
        // 可选：处理拖拽进入
        // 可以在这里改变视觉反馈，但同样要消费事件
        scene.setOnDragEntered(Event::consume);
        // 可选：处理拖拽离开
        // 恢复视觉反馈，消费事件
        scene.setOnDragExited(Event::consume);
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
        }
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> handleExit());
        primaryStage.show();

        // ==================== JavaFX 层面滚动条处理 (修正版) ====================
        // 这是一个备用方案，通过反射和定时器来强制隐藏可能出现的滚动条

        // 1. 延迟首次隐藏滚动条的尝试，确保UI组件已完全加载和渲染
        Platform.runLater(this::hideWebViewScrollBars);

        // 2. 启动一个定时器，定期检查并隐藏滚动条
        // 这可以捕获到在UI加载后动态创建的滚动条
        Timeline scrollbarHider = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            if (logWebView != null) {
                hideWebViewScrollBars();
            }
        }));
        scrollbarHider.setCycleCount(Timeline.INDEFINITE);
        scrollbarHider.play();
        // ==================== JavaFX 层面处理结束 ====================


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

    /**
     * 使用反射尝试隐藏WebView内部的滚动条节点。
     * 这是一个备用方案，因为CSS和JavaScript方法可能不总是有效。
     * 注意：此方法依赖于JavaFX的内部实现，可能在未来的版本中失效。
     */
    private void hideWebViewScrollBars() {
        if (logWebView == null) return;
        try {
            // WebView的内部结构可能会随Java版本变化，这是一种比较脆弱的方法
            // 我们尝试查找所有类型名包含"ScrollBar"的节点
            for (Node node : logWebView.lookupAll("*")) {
                if (node.getClass().getName().contains("ScrollBar")) {
                    // 通过多种方式确保滚动条不可见且不参与布局
                    node.setVisible(false);
                    node.setManaged(false); // 从布局计算中移除
                    node.setOpacity(0);
                    node.resize(0, 0); // 尝试将其大小设置为0
                }
            }
        } catch (Exception e) {
            // 忽略所有异常，因为这是对内部实现的hack
            // 在生产环境中，可以考虑记录日志来调试
            // System.err.println("Warning: Failed to hide scrollbars via reflection: " + e.getMessage());
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

    private VBox createAdvancedSettingsGroup() {
        // 创建高级设置面板
        // 高级设置相关控件 - 使用ToggleButton替代CheckBox
        TitledPane advancedSettingsPane = new TitledPane();
        advancedSettingsPane.setText("高级设置");
        advancedSettingsPane.setExpanded(false); // 默认折叠
        advancedSettingsPane.getStyleClass().add("titled-pane");

        // 创建高级设置内容
        GridPane advancedGrid = new GridPane();
        advancedGrid.setHgap(15);
        advancedGrid.setVgap(15);
        advancedGrid.setPadding(new Insets(15));

        // 本地域名设置
        Label localDomainLabel = new Label("本地域名:");
        localDomainField = new TextField();
        localDomainField.setPromptText("本地域名 (默认: localhost)");
        localDomainField.setText(NeoLink.localDomainName);
        localDomainField.setPrefWidth(200);

        // HOST_HOOK_PORT设置
        Label hostHookPortLabel = new Label("服务端口:");
        hostHookPortField = new TextField();
        hostHookPortField.setPromptText("服务端口 (默认: 44801)");
        hostHookPortField.setText(String.valueOf(NeoLink.hostHookPort));
        hostHookPortField.setPrefWidth(200);

        // HOST_CONNECT_PORT设置
        Label hostConnectPortLabel = new Label("连接端口:");
        hostConnectPortField = new TextField();
        hostConnectPortField.setPromptText("连接端口 (默认: 44802)");
        hostConnectPortField.setText(String.valueOf(NeoLink.hostConnectPort));
        hostConnectPortField.setPrefWidth(200);

        // TCP/UDP开关 - 使用自定义复选框
        Label protocolLabel = new Label("协议启用:");
        HBox protocolBox = new HBox(15);
        HBox tcpBox = createCustomCheckBox("启用TCP", !NeoLink.isDisableTCP);
        HBox udpBox = createCustomCheckBox("启用UDP", !NeoLink.isDisableUDP);
        protocolBox.getChildren().addAll(tcpBox, udpBox);

        // 自动重连开关 - 使用自定义复选框
        Label reconnectLabel = new Label("自动重连:");
        HBox reconnectBox = createCustomCheckBox("启用自动重连", NeoLink.enableAutoReconnect);

        // 添加到网格
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

        // 设置列宽
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
        logWebView.setContextMenuEnabled(false);

        // --- 添加自定义右键菜单 ---
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> {
            String script = "window.getSelection().toString();";
            Object result = logWebView.getEngine().executeScript(script);
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
            Object result = logWebView.getEngine().executeScript(script);
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

        // --- 添加 JavaFX 拖放事件处理 ---
        logWebView.setOnDragOver(Event::consume);
        logWebView.setOnDragEntered(Event::consume);
        logWebView.setOnDragExited(Event::consume);
        logWebView.setOnDragDropped(event -> {
            event.setDropCompleted(false);
            event.consume();
        });

        // 创建HTML内容，包含scroll-container并使用最激进的方法隐藏滚动条
        String initialHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        /* 基本样式 */
                        html, body {
                            background-color: #0c0c0c;
                            color: #cccccc;
                            font-family: 'Consolas', 'Courier New', monospace;
                            font-size: 13px;
                            margin: 0;
                            padding: 0;
                            height: 100%;
                            overflow: hidden;
                        }
                
                        /* 创建一个可滚动的容器 */
                        #scroll-container {
                            height: 100vh;
                            overflow-y: auto;
                            overflow-x: hidden;
                            padding: 12px;
                            box-sizing: border-box;
                            white-space: pre-wrap;
                            word-wrap: break-word;
                            /* 使用负边距隐藏滚动条 */
                            margin-right: -17px;
                            padding-right: 17px;
                        }
                
                        /* 隐藏所有滚动条 - 最激进的方法 */
                        ::-webkit-scrollbar {
                            display: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            background: transparent !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                
                        ::-webkit-scrollbar-track {
                            display: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            background: transparent !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                
                        ::-webkit-scrollbar-thumb {
                            display: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            background: transparent !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                
                        ::-webkit-scrollbar-button {
                            display: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            background: transparent !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                
                        ::-webkit-scrollbar-corner {
                            display: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            background: transparent !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                
                        ::-webkit-scrollbar-resizer {
                            display: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            background: transparent !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                
                        /* Firefox 滚动条 */
                        html {
                            scrollbar-width: none !important;
                        }
                
                        /* IE/Edge 滚动条 */
                        body {
                            -ms-overflow-style: none !important;
                        }
                
                        /* 通用隐藏滚动条 */
                        * {
                            scrollbar-width: none !important;
                            -ms-overflow-style: none !important;
                        }
                
                        /* 使用更通用的选择器 */
                        [style*="overflow"] {
                            scrollbar-width: none !important;
                            -ms-overflow-style: none !important;
                        }
                
                        /* 隐藏所有可能的滚动条 */
                        div::-webkit-scrollbar,
                        span::-webkit-scrollbar,
                        p::-webkit-scrollbar,
                        pre::-webkit-scrollbar,
                        code::-webkit-scrollbar,
                        body::-webkit-scrollbar,
                        html::-webkit-scrollbar {
                            display: none !important;
                            width: 0px !important;
                            height: 0px !important;
                            background: transparent !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                    </style>
                </head>
                <body>
                    <div id="scroll-container"></div>
                </body>
                </html>""";
        logWebView.getEngine().loadContent(initialHtml);

        // 在页面加载完成后，使用JavaScript完全移除滚动条
        logWebView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == javafx.concurrent.Worker.State.SUCCEEDED) {
                // 使用JavaScript完全移除滚动条
                logWebView.getEngine().executeScript(
                        "var style = document.createElement('style');" +
                                "style.innerHTML = `" +
                                "  *::-webkit-scrollbar { display: none !important; width: 0px !important; height: 0px !important; visibility: hidden !important; opacity: 0 !important; }" +
                                "  *::-webkit-scrollbar-track { display: none !important; width: 0px !important; height: 0px !important; visibility: hidden !important; opacity: 0 !important; }" +
                                "  *::-webkit-scrollbar-thumb { display: none !important; width: 0px !important; height: 0px !important; visibility: hidden !important; opacity: 0 !important; }" +
                                "  *::-webkit-scrollbar-button { display: none !important; width: 0px !important; height: 0px !important; visibility: hidden !important; opacity: 0 !important; }" +
                                "  *::-webkit-scrollbar-corner { display: none !important; width: 0px !important; height: 0px !important; visibility: hidden !important; opacity: 0 !important; }" +
                                "  *::-webkit-scrollbar-resizer { display: none !important; width: 0px !important; height: 0px !important; visibility: hidden !important; opacity: 0 !important; }" +
                                "  * { scrollbar-width: none !important; -ms-overflow-style: none !important; }" +
                                "  #scroll-container { margin-right: -17px; padding-right: 17px; }" +
                                "`;" +
                                "document.head.appendChild(style);" +
                                "// 尝试移除所有滚动条元素" +
                                "function hideScrollbars() {" +
                                "  var allElements = document.getElementsByTagName('*');" +
                                "  for (var i = 0; i < allElements.length; i++) {" +
                                "    var element = allElements[i];" +
                                "    var style = window.getComputedStyle(element);" +
                                "    if (style.overflow === 'scroll' || style.overflow === 'auto') {" +
                                "      element.style.overflow = 'hidden';" +
                                "    }" +
                                "  }" +
                                "}" +
                                "hideScrollbars();" +
                                "// 定期检查并隐藏滚动条" +
                                "setInterval(hideScrollbars, 100);"
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

    private void startLogConsumer() {
        // 使用一个单线程的 ExecutorService 即可，不再需要 ScheduledExecutorService
        logConsumerExecutor = Executors.newSingleThreadExecutor();
        logConsumerExecutor.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 👉 关键改动：使用 take() 阻塞等待新日志
                    // 当队列为空时，线程会在这里休眠，不消耗CPU
                    // 当有新日志时，线程会被唤醒，继续执行
                    String message = LogMessageQueue.take();
                    appendLogToWebView(message);
                }
            } catch (InterruptedException e) {
                // 这是正常的退出方式（通过 executor.shutdownNow()）
                Thread.currentThread().interrupt();
            }
        });
    }

    private void appendLogToWebView(String ansiText) {
        Platform.runLater(() -> {
            if (logWebView == null) return;

            // 使用JavaScript直接向滚动容器添加内容
            String script = "var container = document.getElementById('scroll-container');" +
                    "if (container) {" +
                    "  var logDiv = document.createElement('div');" +
                    "  logDiv.style.whiteSpace = 'pre-wrap';" +
                    "  logDiv.style.wordWrap = 'break-word';";

            if (!ansiText.contains("\033[")) {
                script += "  logDiv.textContent = `" + escapeJsString(ansiText) + "`;";
            } else {
                script += "  logDiv.innerHTML = `" + parseAnsiToHtml(ansiText) + "`;";
            }

            script += "  container.appendChild(logDiv);" +
                    "  container.scrollTop = container.scrollHeight;" +
                    "}";

            logWebView.getEngine().executeScript(script);

            // 立即调用JavaFX层面的隐藏滚动条方法
            hideWebViewScrollBars();
        });
    }

    private void startService() {
        if (isRunning) return;
        if (!validateForm()) return;//检查输入

        resetNeoLinkState();

        NeoLink.remoteDomainName = remoteDomainField.getText().trim();
        NeoLink.localPort = Integer.parseInt(localPortField.getText().trim());
        NeoLink.key = accessKeyField.getText();

        // 应用高级设置
        applyAdvancedSettings();

        NeoLink.say("正在启动 NeoLink 服务...");
        NeoLink.printBasicInfo();
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

        // 验证高级设置中的端口
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
        if (str == null) {
            return "";
        }
        // 必须先替换反斜杠，否则会转义后续的字符
        return str.replace("\\", "\\\\")
                .replace("`", "\\`")  // 转义模板字符串的反引号
                .replace("$", "\\$")  // 转义模板字符串的插值符号
                .replace("\n", "\\n") // 转义换行符
                .replace("\r", "\\r") // 转义回车符
                .replace("\t", "\\t"); // 转义制表符
    }

    /**
     * 辅助方法：将包含ANSI颜色代码的字符串转换为HTML格式。
     *
     * @param ansiText 包含ANSI代码的字符串
     * @return 转换后的HTML字符串
     */
    private String parseAnsiToHtml(String ansiText) {
        if (ansiText == null) {
            return "";
        }
        // 首先转义所有特殊字符，防止HTML/JS注入
        String html = escapeJsString(ansiText);

        // 然后替换ANSI颜色码为HTML <span> 标签
        // 注意：顺序很重要，先替换颜色，最后替换重置码
        html = html.replaceAll("\033\\[31m", "<span style='color: #ff5555;'>");
        html = html.replaceAll("\033\\[32m", "<span style='color: #50fa7b;'>");
        html = html.replaceAll("\033\\[33m", "<span style='color: #f1fa8c;'>");
        html = html.replaceAll("\033\\[34m", "<span style='color: #bd93f9;'>");
        html = html.replaceAll("\033\\[35m", "<span style='color: #ff79c6;'>");
        html = html.replaceAll("\033\\[36m", "<span style='color: #8be9fd;'>");
        // 重置所有样式
        html = html.replaceAll("\033\\[0m", "</span>");

        return html;
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            try {
                // 创建自定义对话框窗口 - 完全照抄主窗口的初始化方式
                Stage dialogStage = new Stage();
                dialogStage.initStyle(StageStyle.UNDECORATED);
                dialogStage.initOwner(primaryStage);
                dialogStage.initModality(Modality.APPLICATION_MODAL); // 关键：设置为应用模态，冻结主UI
                dialogStage.setResizable(false);

                // 创建主布局 - 完全照抄主窗口的createMainLayout结构
                VBox root = new VBox();
                root.getChildren().add(createCustomTitleBarForDialog(dialogStage, "输入错误"));

                BorderPane contentPane = new BorderPane();
                contentPane.setPadding(new Insets(24));

                // 创建内容区域
                VBox centerSection = new VBox(24); // 增加间距
                centerSection.setAlignment(Pos.CENTER);

                // 放大警告图标 - 使用更大的图标和更醒目的样式
                Label warningIcon = new Label("⚠");
                warningIcon.setStyle("-fx-font-size: 48px; -fx-text-fill: #ff9800; -fx-font-weight: bold;");

                // 添加消息文本 - 增大字体并居中
                Label messageLabel = new Label(message);
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(450);
                messageLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-text-alignment: center;");
                messageLabel.setAlignment(Pos.CENTER);

                // 添加确定按钮 - 增大按钮
                Button okButton = new Button("确定");
                okButton.getStyleClass().add("primary-button");
                okButton.setPrefWidth(120);
                okButton.setPrefHeight(36);
                okButton.setStyle("-fx-font-size: 14px; -fx-font-weight: 500;");
                okButton.setOnAction(e -> dialogStage.close());

                // 组装内容 - 垂直居中布局
                centerSection.getChildren().addAll(warningIcon, messageLabel, okButton);
                contentPane.setCenter(centerSection);

                root.getChildren().add(contentPane);
                VBox.setVgrow(contentPane, Priority.ALWAYS);

                // 创建场景 - 完全照抄主窗口的CSS加载方式
                Scene scene = new Scene(new BorderPane(root), 500, 250); // 增大对话框尺寸

                // 使用与主窗口完全相同的CSS文件
                String css = Objects.requireNonNull(MainWindowController.class.getResource("/dark-theme-webview.css")).toExternalForm();
                scene.getStylesheets().add(css);

                // 添加ContextMenu的CSS样式表
                String contextMenuCss = Objects.requireNonNull(
                        MainWindowController.class.getResource("/dark-context-menu.css")
                ).toExternalForm();
                scene.getStylesheets().add(contextMenuCss);

                dialogStage.setScene(scene);

                // 居中显示对话框
                dialogStage.setOnShown(event -> {
                    dialogStage.setX(primaryStage.getX() + primaryStage.getWidth() / 2 - dialogStage.getWidth() / 2);
                    dialogStage.setY(primaryStage.getY() + primaryStage.getHeight() / 2 - dialogStage.getHeight() / 2);
                });

                dialogStage.showAndWait(); // 使用showAndWait()确保模态行为

            } catch (Exception e) {
                // 如果自定义对话框创建失败，回退到标准Alert
                System.err.println("Failed to create custom dialog: " + e.getMessage());
                e.printStackTrace();

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initOwner(primaryStage);
                alert.initModality(Modality.APPLICATION_MODAL); // 确保标准Alert也是模态的
                alert.setTitle("输入错误");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            }
        });
    }

    // 为对话框创建自定义标题栏的方法 - 完全照抄主窗口的createCustomTitleBar方法
    private Region createCustomTitleBarForDialog(Stage dialogStage, String title) {
        HBox titleBar = new HBox();
        titleBar.setPrefHeight(36);
        titleBar.getStyleClass().add("title-bar");

        // Logo: 26px 高
        ImageView logoView = new ImageView();
        try {
            Image logo = new Image(Objects.requireNonNull(MainWindowController.class.getResourceAsStream("/logo.png")));
            logoView.setImage(logo);
            logoView.setFitHeight(26);
            logoView.setPreserveRatio(true);
            HBox.setMargin(logoView, new Insets(0, 10, 0, 10));
        } catch (Exception ignored) {
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title-text");

        // 只使用关闭按钮
        Button closeButton = createTitleBarButton("✕");
        closeButton.getStyleClass().add("close-button");
        closeButton.setOnAction(e -> dialogStage.close());

        HBox controls = new HBox(0, closeButton);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 布局: [logo][title][spacer][controls]
        titleBar.getChildren().addAll(logoView, titleLabel, spacer, controls);

        // 拖拽 & 双击逻辑 - 完全照抄主窗口
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
        // 创建复选框容器
        StackPane checkBox = new StackPane();
        checkBox.setMinSize(18, 18);
        checkBox.setMaxSize(18, 18);
        checkBox.setPrefSize(18, 18);

        // 根据选中状态设置初始背景色
        if (selected) {
            checkBox.setStyle("-fx-background-color: #0078d4; -fx-border-color: #0078d4; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        } else {
            checkBox.setStyle("-fx-background-color: #202020; -fx-border-color: #555555; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        }

        // 使用正确的勾符号，确保字体支持
        Label checkMark = new Label("✔"); // 使用更粗的勾符号
        checkMark.setStyle("-fx-font-family: 'Segoe UI Symbol', 'Arial', sans-serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        checkMark.setVisible(selected);

        // 存储勾标记引用，根据文本内容判断是哪个复选框
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

        // 添加点击事件
        box.setOnMouseClicked(e -> {
            boolean newState = !checkMark.isVisible();
            checkMark.setVisible(newState);
            if (newState) {
                checkBox.setStyle("-fx-background-color: #0078d4; -fx-border-color: #0078d4; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            } else {
                checkBox.setStyle("-fx-background-color: #202020; -fx-border-color: #555555; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }

            // 实时更新NeoLink类的布尔值
            if (text.contains("TCP")) {
                NeoLink.isDisableTCP = !newState; // 选中表示启用TCP，所以isDisableTCP为false
                NeoLink.say("TCP协议已" + (newState ? "启用" : "禁用"));
            } else if (text.contains("UDP")) {
                NeoLink.isDisableUDP = !newState; // 选中表示启用UDP，所以isDisableUDP为false
                NeoLink.say("UDP协议已" + (newState ? "启用" : "禁用"));
            } else if (text.contains("自动重连")) {
                NeoLink.enableAutoReconnect = newState;
                NeoLink.say("自动重连已" + (newState ? "启用" : "禁用"));
            }
        });

        // 添加悬停效果
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

    private void applyAdvancedSettings() {
        // 应用本地域名
        String localDomain = localDomainField.getText().trim();
        if (!localDomain.isEmpty()) {
            NeoLink.localDomainName = localDomain;
        }

        // 应用服务端口
        String hookPortStr = hostHookPortField.getText().trim();
        if (!hookPortStr.isEmpty() && PORT_PATTERN.matcher(hookPortStr).matches()) {
            int hookPort = Integer.parseInt(hookPortStr);
            if (hookPort > 0 && hookPort <= 65535) {
                NeoLink.hostHookPort = hookPort;
            }
        }

        // 应用连接端口
        String connectPortStr = hostConnectPortField.getText().trim();
        if (!connectPortStr.isEmpty() && PORT_PATTERN.matcher(connectPortStr).matches()) {
            int connectPort = Integer.parseInt(connectPortStr);
            if (connectPort > 0 && connectPort <= 65535) {
                NeoLink.hostConnectPort = connectPort;
            }
        }

        // 应用TCP/UDP设置 - 从自定义复选框获取状态
        // 检查勾标记的可见性来判断是否选中
        boolean tcpEnabled = (tcpCheckMark != null && tcpCheckMark.isVisible());
        boolean udpEnabled = (udpCheckMark != null && udpCheckMark.isVisible());
        boolean autoReconnectEnabled = (reconnectCheckMark != null && reconnectCheckMark.isVisible());

        NeoLink.isDisableTCP = !tcpEnabled;
        NeoLink.isDisableUDP = !udpEnabled;
        NeoLink.enableAutoReconnect = autoReconnectEnabled;
    }
}