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
    public static void main(String[] args) {
        launch(args);
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