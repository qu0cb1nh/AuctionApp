package net.auctionapp.client.ui.managers;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.utils.ResourcesUtil;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SceneManager {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SceneManager-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private SceneManager() {
    }

    public static void switchScene(String fxmlFile) {
        Objects.requireNonNull(fxmlFile, "fxmlFile");

        Stage stage = ClientApp.getPrimaryStage();
        if (stage == null) {
            throw new IllegalStateException("Primary stage is not initialized.");
        }

        requireFxmlFilename(fxmlFile);
        URL resource = ResourcesUtil.fxml(fxmlFile);

        Parent root = loadRoot(resource, fxmlFile);
        Parent wrappedRoot = NotificationToastManager.wrapWithNotificationHost(root);
        Scene scene = stage.getScene();
        if (scene != null) {
            scene.setRoot(wrappedRoot);
        } else {
            stage.setScene(new Scene(wrappedRoot, ClientApp.WINDOW_WIDTH, ClientApp.WINDOW_HEIGHT));
        }
        stage.show();
    }

    public static void switchSceneWithDelay(String fxmlFile, long delayMillis) {
        Objects.requireNonNull(fxmlFile, "fxmlFile");
        SCHEDULER.schedule(
                () -> Platform.runLater(() -> switchScene(fxmlFile)),
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

    private static void requireFxmlFilename(String fxmlFile) {
        if (!fxmlFile.endsWith(".fxml")) {
            throw new IllegalArgumentException("FXML file name must include .fxml: " + fxmlFile);
        }
    }
}
