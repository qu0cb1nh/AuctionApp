package net.auctionapp.client.utils;

import javafx.animation.PauseTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.controllers.NotificationToastController;
import net.auctionapp.common.notifications.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Objects;

public final class NotificationToastUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationToastUtil.class);
    private static final double NOTIFICATION_PADDING = 20.0;
    private static final double NOTIFICATION_HIDE_AFTER_SECONDS = 4.0;
    private static WeakReference<VBox> notificationHostRef = new WeakReference<>(null);

    private NotificationToastUtil() {
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
        host.getChildren().addFirst(notificationNode);

        PauseTransition hideDelay = new PauseTransition(Duration.seconds(NOTIFICATION_HIDE_AFTER_SECONDS));
        hideDelay.setOnFinished(event -> host.getChildren().remove(notificationNode));
        hideDelay.play();
    }

    private static HBox buildContent(Notification notification) {
        try {
            FXMLLoader loader = new FXMLLoader(NotificationToastUtil.class
                    .getResource("/net/auctionapp/client/views/NotificationToast.fxml"));
            HBox toast = loader.load();
            NotificationToastController controller = loader.getController();
            controller.setNotification(notification);
            return toast;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load notification toast FXML; using fallback.", e);
            return null;
        }
    }
}
