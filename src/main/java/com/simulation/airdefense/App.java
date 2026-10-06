package com.simulation.airdefense;

import com.simulation.airdefense.controller.MainController;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Akıllı Hava Savunma ve İHA Sürüsü Simülasyonu — JavaFX giriş noktası.
 * <p>
 * Bu uygulama tamamen eğitim ve simülasyon amaçlıdır; gerçek bir sistemin modeli değildir.
 */
public class App extends Application {

    private static final double WIDTH = 1400;
    private static final double HEIGHT = 900;

    private MainController controller;

    @Override
    public void start(Stage stage) {
        controller = new MainController();
        // Varsayılan 1400x900; ekran (ölçeklendirme sonrası) daha küçükse ekrana sığdır.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(WIDTH, screen.getWidth() - 20);
        double height = Math.min(HEIGHT, screen.getHeight() - 50);
        Scene scene = new Scene(controller.getRoot(), width, height);
        scene.getStylesheets().add(
                Objects.requireNonNull(App.class.getResource("/style.css"), "style.css not found").toExternalForm());
        controller.attachScene(scene);

        stage.setTitle("Hava Savunma ve İHA Sürüsü Simülasyonu — eğitim amaçlı");
        stage.setMinWidth(Math.min(1100, width));
        stage.setMinHeight(Math.min(650, height));
        stage.setScene(scene);
        stage.show();
        stage.centerOnScreen();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
