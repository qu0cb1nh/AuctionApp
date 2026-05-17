package net.auctionapp.client;

import javafx.application.Platform;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.services.NetworkService;
import net.auctionapp.client.services.NotificationService;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.NotificationMessage;
import net.auctionapp.common.notifications.Notification;
import net.auctionapp.common.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppLifecycleManager {
    private static final AppLifecycleManager INSTANCE = new AppLifecycleManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(AppLifecycleManager.class);

    private final NetworkService networkService = NetworkService.getInstance();
    private MessageListener<NotificationMessage> notificationPushListener;
    private MessageListener<ErrorMessage> errorListener;

    private AppLifecycleManager() {
    }

    public static AppLifecycleManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        connectToServer();
        registerGlobalMessageListeners();
    }

    public synchronized void shutdown() {
        LOGGER.info("Shutting down application...");
        if (notificationPushListener != null) {
            NotificationService.getInstance().removeNotificationListener(notificationPushListener);
            notificationPushListener = null;
        }
        if (errorListener != null) {
            networkService.removeMessageListener(MessageType.ERROR, errorListener);
            errorListener = null;
        }
        networkService.shutdown();

        // Failsafe to ensure the application exits
        Platform.exit();
        System.exit(0);
    }

    private void connectToServer() {
        String host = ConfigUtil.getServerHost();
        int port = ConfigUtil.getServerPort();
        networkService.connect(host, port);
    }

    private void registerGlobalMessageListeners() {
        if (notificationPushListener != null) {
            return;
        }
        notificationPushListener = this::handleGlobalNotificationPush;
        NotificationService.getInstance().addNotificationListener(notificationPushListener);
        errorListener = this::handleGlobalErrorMessage;
        networkService.addMessageListener(MessageType.ERROR, errorListener);
    }

    private void handleGlobalNotificationPush(NotificationMessage notificationMessage) {
        Notification notification = notificationMessage.getNotification();
        if (notification == null) {
            return;
        }
        Platform.runLater(() -> NotificationToastManager.show(notification));
    }

    private void handleGlobalErrorMessage(ErrorMessage errorMessage) {
        if (errorMessage == null || errorMessage.getErrorMessage() == null) {
            return;
        }
        String text = errorMessage.getErrorMessage().toLowerCase();
        if (!text.contains("banned")) {
            return;
        }
        AuthService.getInstance().setCurrentUser(null, "Guest", null);
        Platform.runLater(() -> SceneManager.switchScene("LoginMenu.fxml"));
    }
}
