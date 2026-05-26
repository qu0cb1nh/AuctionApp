package net.auctionapp.client.ui.managers;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.ui.controllers.components.NotificationToastController;
import net.auctionapp.client.utils.ResourcesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class NotificationToastManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationToastManager.class);
    private static final double NOTIFICATION_PADDING = 20.0;
    private static final double NOTIFICATION_HIDE_AFTER_SECONDS = 4.0;
    private static final int MAX_PENDING_TOASTS = 5;
    private static final VBox NOTIFICATION_HOST = createNotificationHost();
    private static final Queue<ToastData> PENDING_TOASTS = new ConcurrentLinkedQueue<>();
    private static final AudioClip NOTIFICATION_SOUND = loadNotificationSound();

    private NotificationToastManager() {
    }

    public static Parent wrapWithNotificationHost(Parent contentRoot) {
        Objects.requireNonNull(contentRoot, "contentRoot");
        AnchorPane wrapper = new AnchorPane();
        AnchorPane.setTopAnchor(contentRoot, 0.0);
        AnchorPane.setRightAnchor(contentRoot, 0.0);
        AnchorPane.setBottomAnchor(contentRoot, 0.0);
        AnchorPane.setLeftAnchor(contentRoot, 0.0);
        wrapper.getChildren().add(contentRoot);

        if (NOTIFICATION_HOST.getParent() instanceof Pane previousParent) {
            previousParent.getChildren().remove(NOTIFICATION_HOST);
        }
        AnchorPane.setRightAnchor(NOTIFICATION_HOST, NOTIFICATION_PADDING);
        AnchorPane.setBottomAnchor(NOTIFICATION_HOST, NOTIFICATION_PADDING);
        wrapper.getChildren().add(NOTIFICATION_HOST);

        Platform.runLater(NotificationToastManager::flushPendingToasts);
        return wrapper;
    }

    public static void show(String title, String message, boolean playSound) {
        show(title, message, playSound, null);
    }

    public static void show(String title, String message, boolean playSound, String auctionId) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(title, message, playSound, auctionId));
            return;
        }
        ToastData toastData = new ToastData(
                title == null || title.isBlank() ? "Notification" : title,
                message == null ? "" : message,
                playSound,
                auctionId
        );
        if (!canShowToast()) {
            enqueueToast(toastData);
            return;
        }
        showNow(toastData);
    }

    private static void showNow(ToastData toastData) {
        HBox notificationNode = buildContent(toastData.title(), toastData.message());
        if (notificationNode == null) {
            return;
        }
        if (toastData.playSound()) {
            playNotificationSound();
        }
        if (toastData.auctionId() != null && !toastData.auctionId().isBlank()) {
            notificationNode.getStyleClass().add("openable");
            notificationNode.setOnMouseClicked(event -> SceneManager.switchToAuctionDetails(toastData.auctionId()));
        }
        NOTIFICATION_HOST.getChildren().addFirst(notificationNode);

        PauseTransition hideDelay = new PauseTransition(Duration.seconds(NOTIFICATION_HIDE_AFTER_SECONDS));
        hideDelay.setOnFinished(event -> NOTIFICATION_HOST.getChildren().remove(notificationNode));
        hideDelay.play();
    }

    public static void showSuccess(String message) {
        showSuccess(message, false);
    }

    public static void showSuccess(String message, boolean playSound) {
        showStatus("Success", message, playSound);
    }

    public static void showError(String message) {
        showError(message, false);
    }

    public static void showError(String message, boolean playSound) {
        showStatus("Error", message, playSound);
    }

    public static void showInfo(String message) {
        showInfo(message, false);
    }

    public static void showInfo(String message, boolean playSound) {
        showStatus("Info", message, playSound);
    }

    public static void showWarning(String message, boolean playSound) {
        showStatus("Warning", message, playSound);
    }

    private static void showStatus(String title, String message, boolean playSound) {
        show(title, message == null ? "" : message, playSound);
    }

    private static boolean canShowToast() {
        Stage stage = ClientApp.getPrimaryStage();
        return stage != null
                && stage.isShowing()
                && stage.getScene() != null
                && NOTIFICATION_HOST.getScene() != null;
    }

    private static void enqueueToast(ToastData toastData) {
        PENDING_TOASTS.add(toastData);
        while (PENDING_TOASTS.size() > MAX_PENDING_TOASTS) {
            PENDING_TOASTS.poll();
        }
    }

    private static void flushPendingToasts() {
        if (!canShowToast()) {
            return;
        }
        ToastData toastData;
        while ((toastData = PENDING_TOASTS.poll()) != null) {
            showNow(toastData);
        }
    }

    private static HBox buildContent(String title, String message) {
        try {
            FXMLLoader loader = ResourcesUtil.fxmlLoader("components/NotificationToast.fxml");
            HBox toast = loader.load();
            NotificationToastController controller = loader.getController();
            controller.setContent(title, message);
            return toast;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load notification toast FXML.", e);
            return null;
        }
    }

    private static VBox createNotificationHost() {
        VBox host = new VBox(8.0);
        host.setAlignment(Pos.BOTTOM_RIGHT);
        host.setFillWidth(false);
        host.setPickOnBounds(false);
        host.setMouseTransparent(false);
        return host;
    }

    private static AudioClip loadNotificationSound() {
        URL soundResource = ResourcesUtil.sound("notification_sound.mp3");
        if (soundResource == null) {
            LOGGER.warn("Missing notification sound resource.");
            return null;
        }
        try {
            AudioClip clip = new AudioClip(soundResource.toExternalForm());
            clip.setVolume(0.9);
            return clip;
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to initialize notification sound.", e);
            return null;
        }
    }

    private static void playNotificationSound() {
        if (NOTIFICATION_SOUND == null) {
            return;
        }
        try {
            NOTIFICATION_SOUND.play();
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to play notification sound.", e);
        }
    }

    private record ToastData(String title, String message, boolean playSound, String auctionId) {
    }
}
