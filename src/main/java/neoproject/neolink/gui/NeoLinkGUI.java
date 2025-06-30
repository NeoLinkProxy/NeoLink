package neoproject.neolink.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import neoproject.neolink.ConfigOperator;

public class NeoLinkGUI extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        ConfigOperator.readAndSetValue();
        Parent root = FXMLLoader.load(getClass().getResource("/neoproject/neolink/gui/neolink_gui.fxml"));
        Scene scene = new Scene(root, 900, 700);

        // 添加CSS样式
        scene.getStylesheets().add(getClass().getResource("/neoproject/neolink/gui/neolink.css").toExternalForm());

        primaryStage.setTitle("NeoLink - 内网穿透客户端");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}