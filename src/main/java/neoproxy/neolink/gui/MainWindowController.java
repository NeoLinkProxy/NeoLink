package neoproxy.neolink.gui;

import fun.ceroxe.api.print.log.Loggist;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import neoproxy.neolink.ConfigOperator;
import neoproxy.neolink.Debugger;
import neoproxy.neolink.NeoLink;
import neoproxy.neolink.threads.CheckAliveThread;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproxy.neolink.InternetOperator.sendStr;
import static neoproxy.neolink.NeoLink.languageData;

public class MainWindowController {
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");
    private static final int MAX_LOG_ENTRIES = 1000;
    private static boolean shouldAutoStart = false;

    private final Stage primaryStage;
    private final ExecutorService coreExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<String> pendingLogBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);
    private final List<NeoNode> nodeList = new ArrayList<>();
    private final ExecutorService stopExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService logConsumerExecutor;
    private Future<?> currentTask = null;
    private TextField remoteDomainField;
    private HBox nodeSelectorContainer;
    private Label selectorDisplayLabel;
    private Pane selectorIconPane;
    private TextField localPortField;
    private PasswordField accessKeyField;
    private WebView logWebView;
    private WebEngine webEngine;
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
    private Label debugCheckMark;
    private Label showConnCheckMark;
    private Label ppCheckMark;

    public MainWindowController(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public static void setAutoStart(boolean autoStart) {
        shouldAutoStart = autoStart;
    }

    public void show() {
        Debugger.debugOperation("Showing MainWindowController.");
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

        Debugger.debugOperation("Loading nodes configuration...");
        loadNodes();

        primaryStage.initStyle(StageStyle.UNDECORATED);
        Scene scene = new Scene(createMainLayout(), 950, 700);

        try {
            if (MainWindowController.class.getResource("/dark-theme-webview.css") != null)
                scene.getStylesheets().add(MainWindowController.class.getResource("/dark-theme-webview.css").toExternalForm());
            if (MainWindowController.class.getResource("/dark-context-menu.css") != null)
                scene.getStylesheets().add(MainWindowController.class.getResource("/dark-context-menu.css").toExternalForm());
        } catch (Exception ignored) {
        }

        scene.setOnDragOver(Event::consume);
        scene.setOnDragEntered(Event::consume);
        scene.setOnDragExited(Event::consume);
        scene.setOnDragDropped(event -> {
            event.setDropCompleted(false);
            event.consume();
        });

        try {
            Image appIcon = new Image(Objects.requireNonNull(MainWindowController.class.getResourceAsStream("/logo.png")));
            primaryStage.getIcons().add(appIcon);
        } catch (Exception ignored) {
        }

        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> handleExit());
        primaryStage.show();

        Platform.runLater(this::hideWebViewScrollBars);
        Timeline scrollbarHider = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            if (logWebView != null) hideWebViewScrollBars();
        }));
        scrollbarHider.setCycleCount(Timeline.INDEFINITE);
        scrollbarHider.play();

        startLogConsumer();
        setupWindowResizeHandlers(scene);

        if (shouldAutoStart) {
            Debugger.debugOperation("Auto-start enabled via GUI.");
            Platform.runLater(() -> {
                if (NeoLink.key != null) accessKeyField.setText(NeoLink.key);
                if (NeoLink.localPort != -1) localPortField.setText(String.valueOf(NeoLink.localPort));
                startService();
            });
        }
    }

    private void loadNodes() {
        nodeList.clear();
        File nodeFile = ConfigOperator.NODE_FILE;
        if (!nodeFile.exists()) {
            Debugger.debugOperation("Node file not found at " + nodeFile.getAbsolutePath());
            return;
        }

        try {
            String content = Files.readString(nodeFile.toPath(), StandardCharsets.UTF_8);
            content = content.replace("\uFEFF", "");

            String jsonClean = content.replaceAll("/\\*[\\s\\S]*?\\*/", "")
                    .replaceAll("(?m)^\\s*//.*", "")
                    .trim();

            if (jsonClean.startsWith("[") && jsonClean.endsWith("]")) {
                Pattern objectPattern = Pattern.compile("\\{([^{}]+)\\}");
                Matcher matcher = objectPattern.matcher(jsonClean);

                int count = 0;
                while (matcher.find()) {
                    String objStr = matcher.group(1);
                    String name = extractJsonValue(objStr, "name");
                    String address = extractJsonValue(objStr, "address");
                    String icon = extractJsonValue(objStr, "icon");
                    String hookPortStr = extractJsonValue(objStr, "HOST_HOOK_PORT");
                    String connectPortStr = extractJsonValue(objStr, "HOST_CONNECT_PORT");

                    int hookPort = parseIntSafe(hookPortStr, 44801);
                    int connPort = parseIntSafe(connectPortStr, 44802);

                    if (name != null && address != null && !name.isBlank() && !address.isBlank()) {
                        nodeList.add(new NeoNode(name, address, icon, hookPort, connPort));
                        count++;
                    }
                }
                Debugger.debugOperation("Loaded " + count + " nodes.");
            }
        } catch (IOException e) {
            System.err.println("Reading node file failed: " + e.getMessage());
            Debugger.debugOperation(e);
        }
    }

    private String extractJsonValue(String jsonContent, String key) {
        if (jsonContent == null || key == null) return null;
        String searchKey = "\"" + key + "\"";
        int keyIndex = jsonContent.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int cursor = keyIndex + searchKey.length();
        while (cursor < jsonContent.length()) {
            if (jsonContent.charAt(cursor) == ':') {
                cursor++;
                break;
            }
            cursor++;
        }

        while (cursor < jsonContent.length()) {
            if (!Character.isWhitespace(jsonContent.charAt(cursor))) break;
            cursor++;
        }

        if (cursor >= jsonContent.length()) return null;

        if (jsonContent.charAt(cursor) == '"') {
            cursor++;
            StringBuilder sb = new StringBuilder();
            boolean inEscape = false;
            while (cursor < jsonContent.length()) {
                char c = jsonContent.charAt(cursor);
                if (inEscape) {
                    sb.append(c);
                    inEscape = false;
                } else {
                    if (c == '\\') inEscape = true;
                    else if (c == '"') return sb.toString();
                    else sb.append(c);
                }
                cursor++;
            }
        } else {
            StringBuilder sb = new StringBuilder();
            while (cursor < jsonContent.length()) {
                char c = jsonContent.charAt(cursor);
                if (Character.isDigit(c)) sb.append(c);
                else break;
                cursor++;
            }
            return sb.toString();
        }
        return null;
    }

    private int parseIntSafe(String val, int def) {
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return def;
        }
    }

    private Node createIconFromSvg(String svgContent, double targetSize) {
        if (svgContent == null || svgContent.trim().isEmpty()) {
            return new Circle(targetSize / 2, Color.web("#00ff88"));
        }

        try {
            // Debugger.debugOperation("Parsing SVG icon..."); // Commented out to prevent flood
            String cleanSvg = svgContent.trim();
            if (cleanSvg.startsWith("\"") && cleanSvg.endsWith("\"") && cleanSvg.length() > 2) {
                cleanSvg = cleanSvg.substring(1, cleanSvg.length() - 1);
            }
            cleanSvg = cleanSvg.replace("\\\"", "\"").replace("\\\\", "\\");
            cleanSvg = cleanSvg.replace("\n", " ").replace("\r", " ");
            cleanSvg = cleanSvg.replaceAll("='([^']*)'", "=\"$1\"");

            String lowerSvg = cleanSvg.toLowerCase();
            int startIndex = lowerSvg.indexOf("<svg");
            int endIndex = lowerSvg.lastIndexOf("</svg>");

            if (startIndex >= 0) {
                if (endIndex > startIndex) {
                    cleanSvg = cleanSvg.substring(startIndex, endIndex + 6);
                } else {
                    cleanSvg = cleanSvg.substring(startIndex) + "</svg>";
                }
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
            }
            factory.setIgnoringComments(true);
            factory.setNamespaceAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) {
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXParseException {
                    throw e;
                }
            });

            Document doc = builder.parse(new InputSource(new ByteArrayInputStream(cleanSvg.getBytes(StandardCharsets.UTF_8))));
            Element root = doc.getDocumentElement();
            Group svgGroup = new Group();

            parseSvgNode(root, svgGroup);

            double width = 640;
            double height = 480;

            if (root.hasAttribute("viewBox")) {
                String[] vb = root.getAttribute("viewBox").split("[\\s,]+");
                if (vb.length == 4) {
                    width = parseDouble(vb[2]);
                    height = parseDouble(vb[3]);
                }
            } else {
                if (root.hasAttribute("width")) width = parseDouble(root.getAttribute("width"));
                if (root.hasAttribute("height")) height = parseDouble(root.getAttribute("height"));
            }

            if (width <= 0) width = targetSize;
            if (height <= 0) height = targetSize;

            double scaleX = targetSize / width;
            double scaleY = targetSize / height;
            double scale = Math.min(scaleX, scaleY);

            svgGroup.setScaleX(scale);
            svgGroup.setScaleY(scale);

            StackPane container = new StackPane(svgGroup);
            container.setMinSize(targetSize, targetSize);
            container.setMaxSize(targetSize, targetSize);
            Rectangle clip = new Rectangle(targetSize, targetSize);
            container.setClip(clip);

            return container;

        } catch (Exception e) {
            Debugger.debugOperation("SVG Parse Error: " + e.getMessage());
            return new Circle(targetSize / 2, Color.GRAY);
        }
    }

    private void parseSvgNode(org.w3c.dom.Node xmlNode, Group parentGroup) {
        NodeList children = xmlNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String tagName = elem.getTagName().toLowerCase();
                javafx.scene.shape.Shape fxShape = null;

                try {
                    switch (tagName) {
                        case "g":
                            Group group = new Group();
                            parseSvgNode(elem, group);
                            parentGroup.getChildren().add(group);
                            break;
                        case "path":
                            String dPath = elem.getAttribute("d");
                            if (dPath != null && !dPath.isBlank()) {
                                SVGPath path = new SVGPath();
                                path.setContent(dPath);
                                fxShape = path;
                            }
                            break;
                        case "circle":
                            fxShape = new Circle(
                                    parseDouble(elem.getAttribute("cx")),
                                    parseDouble(elem.getAttribute("cy")),
                                    parseDouble(elem.getAttribute("r"))
                            );
                            break;
                        case "rect":
                            double w = parseDouble(elem.getAttribute("width"));
                            double h = parseDouble(elem.getAttribute("height"));
                            if (w > 0 && h > 0) {
                                fxShape = new Rectangle(
                                        parseDouble(elem.getAttribute("x")),
                                        parseDouble(elem.getAttribute("y")),
                                        w, h
                                );
                                if (elem.hasAttribute("rx"))
                                    ((Rectangle) fxShape).setArcWidth(parseDouble(elem.getAttribute("rx")) * 2);
                                if (elem.hasAttribute("ry"))
                                    ((Rectangle) fxShape).setArcHeight(parseDouble(elem.getAttribute("ry")) * 2);
                            }
                            break;
                        case "polygon":
                        case "polyline":
                            Polygon polygon = new Polygon();
                            String points = elem.getAttribute("points");
                            if (points != null && !points.isEmpty()) {
                                String[] pts = points.split("[\\s,]+");
                                for (String pt : pts) {
                                    if (!pt.trim().isEmpty()) polygon.getPoints().add(parseDouble(pt));
                                }
                            }
                            fxShape = polygon;
                            break;
                    }

                    if (fxShape != null) {
                        String fill = elem.getAttribute("fill");
                        if ("none".equalsIgnoreCase(fill)) fxShape.setFill(Color.TRANSPARENT);
                        else if (fill != null && !fill.isEmpty()) {
                            try {
                                fxShape.setFill(Color.web(fill));
                            } catch (Exception e) {
                                fxShape.setFill(Color.BLACK);
                            }
                        } else {
                            fxShape.setFill(Color.BLACK);
                        }

                        String opacity = elem.getAttribute("opacity");
                        if (!opacity.isEmpty()) fxShape.setOpacity(parseDouble(opacity));

                        String transform = elem.getAttribute("transform");
                        if (transform != null && transform.contains("translate")) {
                            Matcher m = Pattern.compile("translate\\s*\\(\\s*([\\d.\\-eE]+)[\\s,]*([\\d.\\-eE]*)\\s*\\)").matcher(transform);
                            if (m.find()) {
                                fxShape.setTranslateX(parseDouble(m.group(1)));
                                if (m.groupCount() > 1 && !m.group(2).isEmpty())
                                    fxShape.setTranslateY(parseDouble(m.group(2)));
                            }
                        }
                        parentGroup.getChildren().add(fxShape);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private double parseDouble(String val) {
        if (val == null || val.isBlank()) return 0;
        try {
            val = val.trim();
            Matcher matcher = Pattern.compile("^-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?").matcher(val);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group());
            }
            return 0;
        } catch (Exception e) {
            return 0;
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
        remoteDomainField.setText(NeoLink.remoteDomainName);
        remoteDomainField.setPromptText("远程服务器地址 (必填)");
        remoteDomainField.setPrefWidth(220);

        Node remoteInputNode;

        if (nodeList != null && !nodeList.isEmpty()) {
            StackPane remoteInputContainer = new StackPane();
            remoteInputContainer.setAlignment(Pos.CENTER_LEFT);

            nodeSelectorContainer = createNodeSelectorUI();

            HBox customInputBox = new HBox(5);
            customInputBox.setAlignment(Pos.CENTER_LEFT);
            customInputBox.setVisible(false);

            Button backToListBtn = new Button("≡");
            backToListBtn.setTooltip(new Tooltip("返回节点列表"));
            backToListBtn.getStyleClass().add("button");
            backToListBtn.setStyle("-fx-padding: 8px 12px; -fx-font-size: 14px;");
            backToListBtn.setOnAction(e -> {
                customInputBox.setVisible(false);
                nodeSelectorContainer.setVisible(true);
            });

            remoteDomainField.setPrefWidth(180);
            customInputBox.getChildren().addAll(remoteDomainField, backToListBtn);

            remoteInputContainer.getChildren().addAll(nodeSelectorContainer, customInputBox);

            selectNode(nodeList.get(0));

            remoteInputNode = remoteInputContainer;
        } else {
            remoteInputNode = remoteDomainField;
        }

        localPortField = new TextField();
        localPortField.setPromptText("本地服务端口 (必填)");
        localPortField.setText(String.valueOf(NeoLink.localPort == -1 ? "" : NeoLink.localPort));
        localPortField.setPrefWidth(160);

        accessKeyField = new PasswordField();
        accessKeyField.setPromptText("访问密钥 (必填)");
        if (NeoLink.key != null) accessKeyField.setText(NeoLink.key);
        accessKeyField.setPrefWidth(220);

        flowPane.getChildren().addAll(
                createLabeledField("远程服务器:", remoteInputNode),
                createLabeledField("本地端口:", localPortField),
                createLabeledField("访问密钥:", accessKeyField)
        );
        return createTitledGroup(flowPane);
    }

    private HBox createNodeSelectorUI() {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(220);
        box.getStyleClass().add("text-field");
        box.setStyle("-fx-cursor: hand;");

        selectorIconPane = new StackPane();
        selectorIconPane.setPrefSize(20, 15);

        selectorDisplayLabel = new Label("选择节点");
        selectorDisplayLabel.setStyle("-fx-text-fill: white; -fx-font-weight: 500;");
        HBox.setHgrow(selectorDisplayLabel, Priority.ALWAYS);

        Label arrow = new Label("▼");
        arrow.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10px;");

        box.getChildren().addAll(selectorIconPane, selectorDisplayLabel, arrow);
        box.setOnMouseClicked(e -> showNodeDropdown(box));

        return box;
    }

    private void showNodeDropdown(Node anchor) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("context-menu");

        for (NeoNode node : nodeList) {
            MenuItem item = new MenuItem(node.getName());
            item.setGraphic(createIconFromSvg(node.getIconSvg(), 16));
            item.setOnAction(e -> selectNode(node));
            menu.getItems().add(item);
        }

        menu.getItems().add(new SeparatorMenuItem());

        MenuItem customItem = new MenuItem("自定义 (Custom Input)");
        Label gear = new Label("⚙");
        gear.setStyle("-fx-text-fill: #aaa;");
        customItem.setGraphic(gear);
        customItem.setOnAction(e -> enableCustomInputMode());
        menu.getItems().add(customItem);

        Point2D screenPos = anchor.localToScreen(0, anchor.getBoundsInLocal().getHeight());
        if (screenPos != null) {
            menu.show(anchor, screenPos.getX(), screenPos.getY());
        } else {
            menu.show(anchor, 0, 0);
        }
    }

    private void selectNode(NeoNode node) {
        if (selectorDisplayLabel != null) selectorDisplayLabel.setText(node.getName());
        if (selectorIconPane != null) {
            selectorIconPane.getChildren().clear();
            selectorIconPane.getChildren().add(createIconFromSvg(node.getIconSvg(), 18));
        }

        remoteDomainField.setText(node.getAddress());

        Platform.runLater(() -> {
            if (hostHookPortField != null) hostHookPortField.setText(String.valueOf(node.getHookPort()));
            if (hostConnectPortField != null) hostConnectPortField.setText(String.valueOf(node.getConnectPort()));
            NeoLink.hostHookPort = node.getHookPort();
            NeoLink.hostConnectPort = node.getConnectPort();
            Debugger.debugOperation("Node selected: " + node.getName());
        });
    }

    private void enableCustomInputMode() {
        if (nodeSelectorContainer != null) {
            nodeSelectorContainer.setVisible(false);
            if (nodeSelectorContainer.getParent() instanceof StackPane parent) {
                for (Node child : parent.getChildren()) {
                    if (child != nodeSelectorContainer) {
                        child.setVisible(true);
                        remoteDomainField.requestFocus();
                        break;
                    }
                }
            }
        }
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
        HBox ppBox = createCustomCheckBox("透传真实IP (PPv2)", NeoLink.enableProxyProtocol);
        protocolBox.getChildren().addAll(tcpBox, udpBox, ppBox);

        Label reconnectLabel = new Label("自动重连:");
        HBox reconnectBox = createCustomCheckBox("启用自动重连", NeoLink.enableAutoReconnect);

        Label logSettingsLabel = new Label("日志设置:");
        HBox logSettingsBox = new HBox(15);
        HBox debugBox = createCustomCheckBox("调试模式", NeoLink.isDebugMode);
        HBox showConnBox = createCustomCheckBox("显示连接", NeoLink.showConnection);
        logSettingsBox.getChildren().addAll(debugBox, showConnBox);

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
        advancedGrid.add(logSettingsLabel, 0, 5);
        advancedGrid.add(logSettingsBox, 1, 5);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPrefWidth(100);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPrefWidth(450);
        advancedGrid.getColumnConstraints().addAll(col1, col2);

        advancedSettingsPane.setContent(advancedGrid);
        VBox group = new VBox(5);
        group.getChildren().add(advancedSettingsPane);
        return group;
    }

    private HBox createLabeledField(String labelText, Node field) {
        Label label = new Label(labelText);
        HBox box = new HBox(8, label, field);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox createLogSection() {
        Label logTitle = new Label("运行日志");
        logTitle.getStyleClass().add("log-title");

        logWebView = new WebView();
        webEngine = logWebView.getEngine();
        logWebView.setContextMenuEnabled(false);

        // ... ContextMenu 代码保持不变 ...
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

        logWebView.setOnDragOver(Event::consume);

        // --- 修复开始：优化的 HTML 模板 ---
        String initialHtmlTemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        html, body {
                            background-color: #0c0c0c;
                            color: #cccccc;
                            font-family: 'Consolas', 'Courier New', monospace;
                            font-size: 13px;
                            margin: 0;
                            padding: 0;
                            height: 100%;
                            overflow: hidden; /* 禁用 body 滚动，由 container 接管 */
                        }
                        #scroll-container {
                            height: 100vh;
                            width: 100vw; /* 强制宽度适配视口 */
                            overflow-y: auto;
                            overflow-x: hidden;
                            padding: 10px; /* 正常的内边距 */
                            box-sizing: border-box; /* 确保 padding 不会撑大宽度 */
                
                            /* 文本换行核心修复 */
                            white-space: pre-wrap;       /* 保留空格和换行 */
                            word-wrap: break-word;       /* 旧版兼容 */
                            overflow-wrap: break-word;   /* 标准换行 */
                            word-break: break-all;       /* 强制打断长单词(如密钥)，防止溢出 */
                            tab-size: 4;                 /* 修复制表符对齐问题 */
                        }
                
                        /* 独立的日志行样式，减少内联样式开销 */
                        .log-entry {
                            line-height: 1.4;
                            border-bottom: 1px solid transparent; /* 占位，防止抖动 */
                        }
                
                        ::-webkit-scrollbar { display: none !important; width: 0px; height: 0px; visibility: hidden; opacity: 0; }
                    </style>
                    <script>
                        function appendLogBatch(htmlBatch) {
                            var container = document.getElementById('scroll-container');
                            if (!container) return;
                
                            // 使用 insertAdjacentHTML 性能通常优于创建 div 再 appendChild
                            container.insertAdjacentHTML('beforeend', htmlBatch);
                
                            var maxEntries = MAX_ENTRIES_PLACEHOLDER;
                            var totalNodes = container.children.length;
                
                            // 批量删除多余节点
                            if (totalNodes > maxEntries) {
                                var removeCount = totalNodes - maxEntries;
                                for (var i = 0; i < removeCount; i++) {
                                    container.removeChild(container.firstChild);
                                }
                            }
                
                            // 强制滚动到底部
                            container.scrollTop = container.scrollHeight;
                        }
                    </script>
                </head>
                <body>
                    <div id="scroll-container"></div>
                </body>
                </html>""";
        // --- 修复结束 ---

        String initialHtml = initialHtmlTemplate.replace("MAX_ENTRIES_PLACEHOLDER", String.valueOf(MAX_LOG_ENTRIES));

        webEngine.loadContent(initialHtml, "text/html");

        // 依然保留这个监听器以防万一
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

    private void startLogConsumer() {
        logConsumerExecutor = Executors.newSingleThreadExecutor();
        logConsumerExecutor.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String message = LogMessageQueue.take();
                    String logHtml = buildLogEntryHtml(message);
                    pendingLogBuffer.offer(logHtml);
                    requestUiUpdate();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private String buildLogEntryHtml(String ansiText) {
        StringBuilder sb = new StringBuilder();
        // 使用 CSS 类 .log-entry，去除内联样式，解决 text-indent 或 padding 导致的错位
        sb.append("<div class='log-entry'>");
        if (ansiText.contains("\033[")) {
            sb.append(parseAnsiToHtml(ansiText));
        } else {
            sb.append(escapeJsString(ansiText));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private void requestUiUpdate() {
        if (isUpdatePending.compareAndSet(false, true)) {
            Platform.runLater(this::processLogQueue);
        }
    }

    private void processLogQueue() {
        try {
            if (pendingLogBuffer.isEmpty()) return;
            StringBuilder batchHtml = new StringBuilder();
            String htmlFragment;
            while ((htmlFragment = pendingLogBuffer.poll()) != null) {
                batchHtml.append(htmlFragment);
            }
            if (webEngine != null && webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                String jsCode = "appendLogBatch(`" + batchHtml.toString().replace("`", "\\`").replace("$", "\\$") + "`);";
                webEngine.executeScript(jsCode);
            }
            hideWebViewScrollBars();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isUpdatePending.set(false);
            if (!pendingLogBuffer.isEmpty()) {
                requestUiUpdate();
            }
        }
    }

    private void startService() {
        if (isRunning) return;
        if (!validateForm()) return;

        Debugger.debugOperation("Starting NeoLink Service...");

        // 1. 设置标志位
        isRunning = true;

        // 2. 立即更新 UI 状态
        updateButtonState(true);

        // 3. 准备数据
        resetNeoLinkState();
        NeoLink.remoteDomainName = remoteDomainField.getText().trim();
        NeoLink.localPort = Integer.parseInt(localPortField.getText().trim());
        NeoLink.key = accessKeyField.getText();
        applyAdvancedSettings();

        if (languageData != null) languageData = languageData.flush();

        // [修改] 增加测试模式提醒，方便你确认参数是否传进来了
        if (NeoLink.isTestUpdate) {
            NeoLink.say("\033[33m[TEST-MODE] --test-update 已启用，将上报虚假版本号 0.0.1 触发更新...\033[0m");
        }

        NeoLink.say("正在启动 NeoLink 服务...");
        NeoLink.printBasicInfo();

        // 4. 提交任务
        currentTask = coreExecutor.submit(() -> {
            try {
                NeoLink.mainWindowController = this;
                NeoLinkCoreRunner.runCore(NeoLink.remoteDomainName, NeoLink.localPort, NeoLink.key);
            } catch (Exception e) {
                Debugger.debugOperation("Critical error in runCore submission: " + e.getMessage());
                Platform.runLater(this::resetUIStateToStopped);
            }
        });
    }

    public void resetUIStateToStopped() {
        isRunning = false;
        startButton.setDisable(false);
        stopButton.setDisable(true);
        currentTask = null; // 清除任务引用
        resetNeoLinkState();
    }

    public void forceStopUIState() {
        stopService();
    }

    private void resetNeoLinkState() {
        NeoLink.hookSocket = null;
        NeoLink.connectingSocket = null;
        NeoLink.remotePort = 0;
        NeoLink.isReconnectedOperation = false;
        // 确保不会重置 isTestUpdate，保持命令行传入的参数
        NeoLink.inputScanner = new Scanner(new ByteArrayInputStream(new byte[0]));
        ConfigOperator.readAndSetValue();
    }

    public void stopService() {
        if (!isRunning) return; // 防止重复点击

        NeoLink.say("正在停止 NeoLink 服务...");
        Debugger.debugOperation("Stopping NeoLink Service (Forced)...");

        // 1. 立即禁用所有按钮，防止用户狂点
        startButton.setDisable(true);
        stopButton.setDisable(true);

        // 2. 异步执行清理，避免冻结界面
        stopExecutor.submit(() -> {
            try {
                // A. 通知 Runner 停止循环
                NeoLinkCoreRunner.requestStop();

                // B. 暴力关闭连接中 Socket (打断 socket.connect)
                if (NeoLink.connectingSocket != null && !NeoLink.connectingSocket.isClosed()) {
                    try {
                        Debugger.debugOperation("Force closing connecting socket...");
                        NeoLink.connectingSocket.close();
                    } catch (Exception ignored) {
                    }
                }

                // C. 暴力关闭已建立的 SecureSocket (打断 socket.read)
                if (NeoLink.hookSocket != null) {
                    try {
                        Debugger.debugOperation("Force closing hook socket...");
                        NeoLink.hookSocket.close();
                    } catch (Exception ignored) {
                    }
                }

                // D. 停止保活线程
                CheckAliveThread.stopThread();

                // E. 取消任务
                if (currentTask != null) {
                    Debugger.debugOperation("Cancelling core task...");
                    currentTask.cancel(true);
                }

            } finally {
                // F. 确保 UI 恢复。注意：这里其实 NeoLinkCoreRunner 的 finally 也会调用 resetUIStateToStopped
                // 双重保险：如果 runCore 已经卡死，这里也会重置 UI
                Platform.runLater(() -> {
                    resetUIStateToStopped();
                    NeoLink.say("服务已停止");
                });
            }
        });
    }

    private void updateButtonState(boolean running) {
        startButton.setDisable(running);
        stopButton.setDisable(!running);
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

        String hookPortStr = hostHookPortField.getText().trim();
        if (!hookPortStr.isEmpty() && (!PORT_PATTERN.matcher(hookPortStr).matches() || Integer.parseInt(hookPortStr) < 1 || Integer.parseInt(hookPortStr) > 65535)) {
            errors.append("• 服务端口必须是1-65535之间的数字。 \n");
        }
        String connectPortStr = hostConnectPortField.getText().trim();
        if (!connectPortStr.isEmpty() && (!PORT_PATTERN.matcher(connectPortStr).matches() || Integer.parseInt(connectPortStr) < 1 || Integer.parseInt(connectPortStr) > 65535)) {
            errors.append("• 连接端口必须是1-65535之间的数字。 \n");
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
                .replace("'", "\\'")
                .replace("\"", "\\\"");
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
                try {
                    if (MainWindowController.class.getResource("/dark-theme-webview.css") != null)
                        scene.getStylesheets().add(MainWindowController.class.getResource("/dark-theme-webview.css").toExternalForm());
                } catch (Exception ignored) {
                }
                dialogStage.setScene(scene);
                dialogStage.setOnShown(event -> {
                    dialogStage.setX(primaryStage.getX() + primaryStage.getWidth() / 2 - dialogStage.getWidth() / 2);
                    dialogStage.setY(primaryStage.getY() + primaryStage.getHeight() / 2 - dialogStage.getHeight() / 2);
                });
                dialogStage.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setContentText(message);
                alert.showAndWait();
            }
        });
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

    private void setupWindowResizeHandlers(Scene scene) {
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

    private Region createCustomTitleBar() {
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

    private Region createCustomTitleBarForDialog(Stage dialogStage) {
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
        Label titleLabel = new Label("提示");
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

    private void handleExit() {
        Debugger.debugOperation("App exit requested.");
        // 先尝试优雅停止
        stopService();

        if (logConsumerExecutor != null) logConsumerExecutor.shutdownNow();
        if (stopExecutor != null) stopExecutor.shutdownNow(); // 关闭停止线程池
        if (currentTask != null) currentTask.cancel(true);
        coreExecutor.shutdownNow();

        Platform.exit();
        System.exit(0);
    }

    private HBox createCustomCheckBox(String text, boolean selected) {
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
        } else if (text.contains("调试模式")) {
            debugCheckMark = checkMark;
        } else if (text.contains("显示连接")) {
            showConnCheckMark = checkMark;
        } else if (text.contains("PPv2")) {
            ppCheckMark = checkMark;
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
                Debugger.debugOperation("TCP enabled: " + newState);
            } else if (text.contains("UDP")) {
                NeoLink.isDisableUDP = !newState;
                sendTCPandUDPState();
                NeoLink.say("UDP协议已" + (newState ? "启用" : "禁用"));
                Debugger.debugOperation("UDP enabled: " + newState);
            } else if (text.contains("自动重连")) {
                NeoLink.enableAutoReconnect = newState;
                NeoLink.say("自动重连已" + (newState ? "启用" : "禁用"));
                Debugger.debugOperation("AutoReconnect enabled: " + newState);
            } else if (text.contains("调试模式")) {
                NeoLink.isDebugMode = newState;
                NeoLink.say("调试模式已" + (newState ? "启用" : "禁用"));
                Debugger.debugOperation("DebugMode enabled: " + newState);
            } else if (text.contains("显示连接")) {
                NeoLink.showConnection = newState;
                NeoLink.say("连接日志显示已" + (newState ? "启用" : "禁用"));
                Debugger.debugOperation("ShowConnection enabled: " + newState);
            } else if (text.contains("PPv2")) {
                NeoLink.enableProxyProtocol = newState;
                NeoLink.say("Proxy Protocol (真实IP透传) 已" + (newState ? "启用" : "禁用"));
                Debugger.debugOperation("ProxyProtocol enabled: " + newState);
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
        String command = "";
        if (!NeoLink.isDisableTCP) command = command.concat("T");
        if (!NeoLink.isDisableUDP) command = command.concat("U");
        try {
            if (isRunning && NeoLink.hookSocket != null) {
                sendStr(command);
                Debugger.debugOperation("Synced TCP/UDP state to server: " + command);
            }
        } catch (IOException e) {
            Debugger.debugOperation("Failed to sync TCP/UDP state: " + e.getMessage());
        }
    }

    private void applyAdvancedSettings() {
        Debugger.debugOperation("Applying advanced settings from GUI...");
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
        boolean debugEnabled = (debugCheckMark != null && debugCheckMark.isVisible());
        boolean showConnEnabled = (showConnCheckMark != null && showConnCheckMark.isVisible());
        boolean ppEnabled = (ppCheckMark != null && ppCheckMark.isVisible());

        NeoLink.isDisableTCP = !tcpEnabled;
        NeoLink.isDisableUDP = !udpEnabled;
        NeoLink.enableAutoReconnect = autoReconnectEnabled;
        NeoLink.isDebugMode = debugEnabled;
        NeoLink.showConnection = showConnEnabled;
        NeoLink.enableProxyProtocol = ppEnabled;
    }
}