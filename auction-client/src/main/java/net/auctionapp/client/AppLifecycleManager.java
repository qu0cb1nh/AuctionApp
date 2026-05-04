package net.auctionapp.client;

import javafx.application.Platform;
import net.auctionapp.client.services.MessageListener;
import net.auctionapp.client.services.NetworkService;
import net.auctionapp.client.services.NotificationService;
import net.auctionapp.client.ui.NotificationToastManager;
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
    }

    private void handleGlobalNotificationPush(NotificationMessage notificationMessage) {
        Notification notification = notificationMessage.getNotification();
        if (notification == null) {
            return;
        }
        Platform.runLater(() -> NotificationToastManager.show(notification));
    }
}
