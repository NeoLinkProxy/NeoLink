package neoproject.neolink.gui;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * NeoLink GUI 应用的主启动类。
 * 包含了对启动异常的捕获和处理。
 */
public class AppStart extends Application {
    // 添加一个接受 autoStart 参数的 main 方法
    public static void main(String[] args, boolean autoStart) {
        MainWindowController.setAutoStart(autoStart);
        launch(args);
    }

    // 保持原有的 main 方法，用于不带 autoStart 参数的调用
    public static void main(String[] args) {
        main(args, false); // 默认不自动启动
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            MainWindowController controller = new MainWindowController(primaryStage);
            controller.show();
        } catch (Exception e) {
            e.printStackTrace();
            // 弹出错误对话框
            Alert alert = new Alert(Alert.AlertType.ERROR, "应用启动失败: " + e.getMessage(), ButtonType.OK);
            alert.setTitle("启动错误");
            alert.showAndWait();
            System.exit(-1);
        }
    }
}