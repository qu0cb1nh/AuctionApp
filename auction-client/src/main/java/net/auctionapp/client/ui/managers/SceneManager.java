package net.auctionapp.client.ui.managers;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.services.MessageListener;
import net.auctionapp.client.services.NetworkService;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SceneManager {
    private static final Deque<String> BACK_STACK = new ArrayDeque<>();
    private static final List<Runnable> SCENE_LISTENER_CLEANUPS = new ArrayList<>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SceneManager-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private static String currentFxmlFile;

    private SceneManager() {
    }

    public static void switchScene(String fxmlFile) {
        if (currentFxmlFile != null && !currentFxmlFile.equals(fxmlFile)) {
            BACK_STACK.push(currentFxmlFile);
        }
        loadScene(fxmlFile);
    }

    public static void resetAndSwitchScene(String fxmlFile) {
        BACK_STACK.clear();
        loadScene(fxmlFile);
    }

    public static void goBack() {
        if (BACK_STACK.isEmpty()) {
            return;
        }
        loadScene(BACK_STACK.pop());
    }

    public static void goBackOrSwitchScene(String fallbackFxmlFile) {
        if (!BACK_STACK.isEmpty()) {
            goBack();
            return;
        }
        resetAndSwitchScene(fallbackFxmlFile);
    }

    public static <T extends Message> void registerSceneMessageListener(MessageType type, MessageListener<T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");

        NetworkService networkService = NetworkService.getInstance();
        networkService.addMessageListener(type, listener);
        SCENE_LISTENER_CLEANUPS.add(() -> networkService.removeMessageListener(type, listener));
    }

    private static void loadScene(String fxmlFile) {
        Objects.requireNonNull(fxmlFile, "fxmlFile");

        Stage stage = ClientApp.getPrimaryStage();
        if (stage == null) {
            throw new IllegalStateException("Primary stage is not initialized.");
        }

        requireFxmlFilename(fxmlFile);
        URL resource = ResourcesUtil.fxml(fxmlFile);

        cleanupSceneMessageListeners();
        currentFxmlFile = fxmlFile;

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

    private static void cleanupSceneMessageListeners() {
        for (Runnable cleanup : SCENE_LISTENER_CLEANUPS) {
            cleanup.run();
        }
        SCENE_LISTENER_CLEANUPS.clear();
    }

    public static void switchSceneWithDelay(String fxmlFile, long delayMillis) {
        Objects.requireNonNull(fxmlFile, "fxmlFile");
        SCHEDULER.schedule(
                () -> Platform.runLater(() -> switchScene(fxmlFile)),
                delayMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public static void resetAndSwitchSceneWithDelay(String fxmlFile, long delayMillis) {
        Objects.requireNonNull(fxmlFile, "fxmlFile");
        SCHEDULER.schedule(
                () -> Platform.runLater(() -> resetAndSwitchScene(fxmlFile)),
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
