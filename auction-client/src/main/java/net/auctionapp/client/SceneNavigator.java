package net.auctionapp.client;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SceneNavigator {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SceneNavigator-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private SceneNavigator() {
    }

    public static void switchScene(String fxmlPath) {
        Objects.requireNonNull(fxmlPath, "fxmlPath");

        Stage stage = ClientApp.getPrimaryStage();
        if (stage == null) {
            throw new IllegalStateException("Primary stage is not initialized.");
        }

        URL resource = SceneNavigator.class.getResource(fxmlPath);
        if (resource == null) {
            throw new IllegalStateException("Missing FXML resource: " + fxmlPath);
        }

        Parent root = loadRoot(resource, fxmlPath);
        Scene scene = stage.getScene();
        if (scene != null) {
            scene.setRoot(root);
        } else {
            stage.setScene(new Scene(root, ClientApp.WINDOW_WIDTH, ClientApp.WINDOW_HEIGHT));
        }
        stage.show();
    }

    public static void switchSceneWithDelay(String fxmlPath, long delayMillis) {
        Objects.requireNonNull(fxmlPath, "fxmlPath");
        SCHEDULER.schedule(
                () -> Platform.runLater(() -> switchScene(fxmlPath)),
                delayMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private static Parent loadRoot(URL resource, String fxmlPath) {
        try {
            return FXMLLoader.load(resource);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load FXML: " + fxmlPath, e);
        }
    }
}
