package net.auctionapp.client.ui.managers;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
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
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class NotificationToastManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationToastManager.class);
    private static final double NOTIFICATION_PADDING = 20.0;
    private static final double NOTIFICATION_HIDE_AFTER_SECONDS = 4.0;
    private static final int MAX_PENDING_TOASTS = 5;
    private static WeakReference<VBox> notificationHostRef = new WeakReference<>(null);
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

        VBox host = new VBox(8.0);
        host.setFillWidth(false);
        host.setPickOnBounds(false);
        host.setMouseTransparent(false);
        AnchorPane.setRightAnchor(host, NOTIFICATION_PADDING);
        AnchorPane.setBottomAnchor(host, NOTIFICATION_PADDING);
        wrapper.getChildren().add(host);

        notificationHostRef = new WeakReference<>(host);
        Platform.runLater(NotificationToastManager::flushPendingToasts);
        return wrapper;
    }

    public static void show(String title, String message) {
        show(title, message, false);
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
        Stage stage = ClientApp.getPrimaryStage();
        if (stage == null || !stage.isShowing()) {
            return;
        }
        if (stage.getScene() == null) {
            return;
        }
        VBox host = notificationHostRef.get();
        if (host == null) {
            return;
        }

        HBox notificationNode = buildContent(toastData.title(), toastData.message());
        if (notificationNode == null) {
            return;
        }
        if (toastData.playSound()) {
            playNotificationSound();
        }
        if (toastData.auctionId() != null && !toastData.auctionId().isBlank()) {
            notificationNode.setStyle(notificationNode.getStyle() + " -fx-cursor: hand;");
            notificationNode.setOnMouseClicked(event -> SceneManager.switchToAuctionDetails(toastData.auctionId()));
        }
        host.getChildren().addFirst(notificationNode);

        PauseTransition hideDelay = new PauseTransition(Duration.seconds(NOTIFICATION_HIDE_AFTER_SECONDS));
        hideDelay.setOnFinished(event -> host.getChildren().remove(notificationNode));
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

    public static void showWarning(String message) {
        showWarning(message, false);
    }

    public static void showWarning(String message, boolean playSound) {
        showStatus("Warning", message, playSound);
    }

    private static void showStatus(String title, String message, boolean playSound) {
        show(title, message == null ? "" : message, playSound);
    }

    private static boolean canShowToast() {
        Stage stage = ClientApp.getPrimaryStage();
        VBox host = notificationHostRef.get();
        return stage != null && stage.isShowing() && stage.getScene() != null && host != null && host.getScene() != null;
    }

    private static void enqueueToast(ToastData toastData) {
        PENDING_TOASTS.add(toastData);
        while (PENDING_TOASTS.size() > MAX_PENDING_TOASTS) {
            PENDING_TOASTS.poll();
        }
    }

    private static void flushPendingToasts() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(NotificationToastManager::flushPendingToasts);
            return;
        }
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
