package neoproject.neolink.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import neoproject.neolink.NeoLink;
import neoproject.neolink.gui.utils.FXLoggist;
import plethora.print.log.LogType;
import plethora.print.log.State;
import plethora.time.Time;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainController {

    @FXML private HBox titleBar=new HBox();
    @FXML private TextField serverHostField;
    @FXML private TextField serverPortField;
    @FXML private TextField localHostField;
    @FXML private TextField localPortField;
    @FXML private TextField accessCodeField;
    @FXML private CheckBox debugModeCheck;
    @FXML private Button toggleButton;
    @FXML private StyleClassedTextArea consoleArea;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private CheckBox autoScrollCheck;

    private Thread tunnelThread;
    private FXLoggist fxLoggist;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean autoScrollEnabled = new AtomicBoolean(true);
    private File currentLogFile;

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        // 设置默认值
        serverHostField.setText(NeoLink.REMOTE_DOMAIN_NAME);
        serverPortField.setText(String.valueOf(NeoLink.HOST_HOOK_PORT));
        localHostField.setText(NeoLink.LOCAL_DOMAIN_NAME);
        localPortField.setText("");
        accessCodeField.setText("");
        autoScrollCheck.setSelected(true);

        progressIndicator.setVisible(false);
        toggleButton.setText("启动隧道");

        // 绑定事件
        toggleButton.setOnAction(e -> toggleTunnel());

        // 设置窗口拖动
        setupWindowDragging();
    }

    private void setupWindowDragging() {
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    @FXML
    private void minimizeWindow() {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void closeWindow() {
        // 停止隧道（如果正在运行）
        if (isRunning.get()) {
            stopTunnel();
        }
        System.exit(0);
    }

    @FXML
    private void scrollToBottom() {
        Platform.runLater(() -> {
            consoleArea.moveTo(consoleArea.getLength());
            consoleArea.requestFollowCaret();
            autoScrollEnabled.set(true);
        });
    }

    private void toggleTunnel() {
        if (isRunning.get()) {
            stopTunnel();
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        if (isRunning.get()) return;

        // 重置控制台
        consoleArea.clear();

        // 创建新的日志文件（带时间戳）
        String logFileName = Time.getCurrentTimeAsFileName(false) + ".log";
        currentLogFile = new File(NeoLink.CURRENT_DIR_PATH + File.separator + "logs" +
                File.separator + logFileName);
        fxLoggist = new FXLoggist(currentLogFile, consoleArea);

        // 替换原始日志记录器
        NeoLink.loggist = fxLoggist;

        // 获取配置
        NeoLink.REMOTE_DOMAIN_NAME = serverHostField.getText();
        NeoLink.HOST_HOOK_PORT = Integer.parseInt(serverPortField.getText());
        NeoLink.LOCAL_DOMAIN_NAME = localHostField.getText();
        NeoLink.localPort = Integer.parseInt(localPortField.getText());
        NeoLink.key = accessCodeField.getText();
        NeoLink.IS_DEBUG_MODE = debugModeCheck.isSelected();

        // 禁用自动重连
        NeoLink.ENABLE_AUTO_RECONNECT = false;

        // 确保自动滚动启用
        autoScrollEnabled.set(autoScrollCheck.isSelected());

        // 更新UI状态
        toggleButton.setText("停止隧道");
        progressIndicator.setVisible(true);
        serverHostField.setDisable(true);
        serverPortField.setDisable(true);
        localHostField.setDisable(true);
        localPortField.setDisable(true);
        accessCodeField.setDisable(true);
        debugModeCheck.setDisable(true);

        // 创建可中断的隧道线程
        isRunning.set(true);
        tunnelThread = new Thread(() -> {
            try {
                // 创建新的可中断线程运行隧道
                Thread tunnelWorker = new Thread(() -> {
                    try {
                        NeoLink.main(new String[]{});
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            fxLoggist.say(new State(LogType.ERROR, "GUI", "隧道异常: " + e.getMessage()));
                        }
                    }
                });

                tunnelWorker.setDaemon(true);
                tunnelWorker.start();

                // 等待隧道工作线程结束
                tunnelWorker.join();
            } catch (InterruptedException e) {
                // 被中断时直接退出
                Thread.currentThread().interrupt();
            } finally {
                // 无论成功与否，都停止隧道
                Platform.runLater(() -> stopTunnel());
            }
        });

        tunnelThread.setDaemon(true);
        tunnelThread.start();
    }

    private void stopTunnel() {
        if (!isRunning.get()) return;

        isRunning.set(false);

        // 立即中断隧道线程
        if (tunnelThread != null && tunnelThread.isAlive()) {
            tunnelThread.interrupt();
        }

        // 关闭所有网络连接
        closeAllConnections();

        if (fxLoggist != null) {
            fxLoggist.say(new State(LogType.INFO, "GUI", "隧道已立即停止"));
        }

        // 更新UI状态
        Platform.runLater(() -> {
            toggleButton.setText("启动隧道");
            progressIndicator.setVisible(false);
            serverHostField.setDisable(false);
            serverPortField.setDisable(false);
            localHostField.setDisable(false);
            localPortField.setDisable(false);
            accessCodeField.setDisable(false);
            debugModeCheck.setDisable(false);
        });
    }

    private void closeAllConnections() {
        try {
            if (NeoLink.hookSocket != null && !NeoLink.hookSocket.isClosed()) {
                NeoLink.hookSocket.close();
            }
            if (NeoLink.hookSocketReader != null) {
                NeoLink.hookSocketReader.close();
            }
            if (NeoLink.hookSocketWriter != null) {
                NeoLink.hookSocketWriter.close();
            }
        } catch (Exception e) {
            if (fxLoggist != null) {
                fxLoggist.say(new State(LogType.WARNING, "GUI", "关闭连接时出错: " + e.getMessage()));
            }
        }
    }
}