package net.auctionapp.client.ui.managers;

import javafx.animation.PauseTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.ui.controllers.NotificationToastController;
import net.auctionapp.common.notifications.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class NotificationToastManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationToastManager.class);
    private static final double NOTIFICATION_PADDING = 20.0;
    private static final double NOTIFICATION_HIDE_AFTER_SECONDS = 4.0;
    private static WeakReference<VBox> notificationHostRef = new WeakReference<>(null);
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
        host.setMouseTransparent(true);
        AnchorPane.setRightAnchor(host, NOTIFICATION_PADDING);
        AnchorPane.setBottomAnchor(host, NOTIFICATION_PADDING);
        wrapper.getChildren().add(host);

        notificationHostRef = new WeakReference<>(host);
        return wrapper;
    }

    public static void show(Notification notification) {
        if (notification == null) {
            return;
        }
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

        HBox notificationNode = buildContent(notification);
        if (notificationNode == null) {
            return;
        }
        playNotificationSound();
        host.getChildren().addFirst(notificationNode);

        PauseTransition hideDelay = new PauseTransition(Duration.seconds(NOTIFICATION_HIDE_AFTER_SECONDS));
        hideDelay.setOnFinished(event -> host.getChildren().remove(notificationNode));
        hideDelay.play();
    }

    public static void showSuccess(String message) {
        showStatus("Success", message);
    }

    public static void showError(String message) {
        showStatus("Error", message);
    }

    public static void showInfo(String message) {
        showStatus("Info", message);
    }

    public static void showWarning(String message) {
        showStatus("Warning", message);
    }

    private static void showStatus(String title, String message) {
        show(new Notification(
                UUID.randomUUID().toString(),
                null,
                null,
                title,
                message == null ? "" : message,
                null,
                LocalDateTime.now()
        ));
    }

    private static HBox buildContent(Notification notification) {
        try {
            FXMLLoader loader = new FXMLLoader(NotificationToastManager.class
                    .getResource("/net/auctionapp/client/ui/fxml/NotificationToast.fxml"));
            HBox toast = loader.load();
            NotificationToastController controller = loader.getController();
            controller.setNotification(notification);
            return toast;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load notification toast FXML.", e);
            return null;
        }
    }

    private static AudioClip loadNotificationSound() {
        URL soundResource = NotificationToastManager.class
                .getResource("/net/auctionapp/client/ui/sounds/notification_sound.mp3");
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
}
