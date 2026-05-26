package net.auctionapp.client;

import javafx.application.Platform;
import net.auctionapp.client.exceptions.NetworkException;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.client.services.NetworkService;
import net.auctionapp.client.config.ClientConfig;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auth.ForcedLogoutResponseMessage;
import net.auctionapp.common.messages.notification.NotificationResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.notifications.Notification;
import net.auctionapp.common.notifications.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AppLifecycleManager {
    private static final AppLifecycleManager INSTANCE = new AppLifecycleManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(AppLifecycleManager.class);
    private static final long CONNECTION_CHECK_INTERVAL_SECONDS = 1;

    private final NetworkService networkService = NetworkService.getInstance();
    private MessageListener<NotificationResponseMessage> notificationPushListener;
    private MessageListener<ErrorResponseMessage> errorListener;
    private MessageListener<ForcedLogoutResponseMessage> forcedLogoutListener;
    private ScheduledExecutorService connectionMonitor;
    private boolean connectionInitialized;
    private boolean lastConnected;

    private AppLifecycleManager() {
    }

    public static AppLifecycleManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        registerGlobalMessageListeners();
        connectToServer();
        startConnectionMonitor();
    }

    public synchronized void shutdown() {
        LOGGER.info("Shutting down application...");
        stopConnectionMonitor();
        if (notificationPushListener != null) {
            networkService.removeMessageListener(MessageType.NOTIFICATION, notificationPushListener);
            notificationPushListener = null;
        }
        if (errorListener != null) {
            networkService.removeMessageListener(MessageType.ERROR, errorListener);
            errorListener = null;
        }
        if (forcedLogoutListener != null) {
            networkService.removeMessageListener(MessageType.FORCED_LOGOUT, forcedLogoutListener);
            forcedLogoutListener = null;
        }
        networkService.shutdown();

        // Failsafe to ensure the application exits
        Platform.exit();
        System.exit(0);
    }

    private void connectToServer() {
        String host = ClientConfig.getServerHost();
        int port = ClientConfig.getServerPort();
        try {
            networkService.connect(host, port);
        } catch (NetworkException e) {
            LOGGER.debug("Connection attempt failed: {}", e.getMessage());
        }
    }

    public void retryConnection() {
        connectToServer();
        boolean connected = networkService.isConnected();
        handleConnectionStatus(connected);
        if (!connected) {
            NotificationToastManager.showWarning("Unable to connect to the server.", false);
        }
    }

    private void registerGlobalMessageListeners() {
        if (notificationPushListener != null) {
            return;
        }
        notificationPushListener = this::handleGlobalNotificationPush;
        networkService.addMessageListener(MessageType.NOTIFICATION, notificationPushListener);
        errorListener = this::handleGlobalErrorMessage;
        networkService.addMessageListener(MessageType.ERROR, errorListener);
        forcedLogoutListener = this::handleForcedLogout;
        networkService.addMessageListener(MessageType.FORCED_LOGOUT, forcedLogoutListener);
    }

    private void handleGlobalNotificationPush(NotificationResponseMessage notificationMessage) {
        Notification notification = notificationMessage.getNotification();
        if (notification == null) {
            return;
        }
        Platform.runLater(() -> NotificationToastManager.show(
                notification.getTitle(),
                notification.getBody(),
                shouldPlayNotificationSound(notification),
                notification.getAuctionId()
        ));
    }

    private void handleGlobalErrorMessage(ErrorResponseMessage errorMessage) {
        if (errorMessage.getErrorMessage() == null) {
            return;
        }
        NotificationToastManager.showError(errorMessage.getErrorMessage());
    }

    private void handleForcedLogout(ForcedLogoutResponseMessage message) {
        AuthService.getInstance().logout();
        if (message.getReason() != null && !message.getReason().isBlank()) {
            NotificationToastManager.showError(message.getReason());
        }
        Platform.runLater(() -> SceneManager.resetAndSwitchScene("LoginMenu.fxml"));
    }

    private synchronized void startConnectionMonitor() {
        if (connectionMonitor != null && !connectionMonitor.isShutdown()) {
            return;
        }
        connectionMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("auction-client-connection-monitor");
            return thread;
        });
        connectionMonitor.scheduleAtFixedRate(
                () -> Platform.runLater(() -> handleConnectionStatus(networkService.isConnected())),
                0,
                CONNECTION_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private synchronized void stopConnectionMonitor() {
        if (connectionMonitor != null && !connectionMonitor.isShutdown()) {
            connectionMonitor.shutdownNow();
        }
        connectionMonitor = null;
    }

    private boolean shouldPlayNotificationSound(Notification notification) {
        NotificationType type = notification.getType();
        return type == NotificationType.OUTBID
                || type == NotificationType.AUCTION_WON
                || type == NotificationType.WATCH_LIST_ENDING_SOON;
    }

    private void handleConnectionStatus(boolean connected) {
        if (!connectionInitialized) {
            connectionInitialized = true;
            lastConnected = connected;
            if (!connected) {
                handleDisconnected("Client is not connected.");
            }
            return;
        }
        if (connected == lastConnected) {
            return;
        }
        lastConnected = connected;
        if (connected) {
            SceneManager.resetAndSwitchScene("LoginMenu.fxml");
            NotificationToastManager.showInfo("Connected to the server. Please log in.");
        } else {
            handleDisconnected("Disconnected from the server.");
        }
    }

    private void handleDisconnected(String notificationText) {
        AuthService.getInstance().logout();
        SceneManager.resetAndSwitchScene("DisconnectedMenu.fxml");
        NotificationToastManager.showWarning(notificationText, true);
    }
}
