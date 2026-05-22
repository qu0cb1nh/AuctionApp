package net.auctionapp.client;

import javafx.application.Platform;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.services.MessageListener;
import net.auctionapp.client.services.NetworkService;
import net.auctionapp.client.config.ClientConfig;
import net.auctionapp.client.services.NotificationService;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.NotificationMessage;
import net.auctionapp.common.notifications.Notification;
import net.auctionapp.common.notifications.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppLifecycleManager {
    private static final AppLifecycleManager INSTANCE = new AppLifecycleManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(AppLifecycleManager.class);
    private static final long CONNECTION_CHECK_INTERVAL_SECONDS = 1;

    private final NetworkService networkService = NetworkService.getInstance();
    private MessageListener<NotificationMessage> notificationPushListener;
    private MessageListener<ErrorMessage> errorListener;
    private final AtomicBoolean autoLoginInProgress = new AtomicBoolean(false);
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
        String host = ClientConfig.getServerHost();
        int port = ClientConfig.getServerPort();
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
        Platform.runLater(() -> NotificationToastManager.show(
                notification.getTitle(),
                notification.getBody(),
                shouldPlayNotificationSound(notification)
        ));
    }

    private void handleGlobalErrorMessage(ErrorMessage errorMessage) {
        if (errorMessage == null || errorMessage.getErrorMessage() == null) {
            return;
        }
        String text = errorMessage.getErrorMessage().toLowerCase();
        if (!text.contains("banned")) {
            NotificationToastManager.showError(errorMessage.getErrorMessage());
            return;
        }
        AuthService.getInstance().clearSessionAndCredentials();
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
                || type == NotificationType.AUCTION_SELLER_RESULT;
    }

    private void handleConnectionStatus(boolean connected) {
        if (!connectionInitialized) {
            connectionInitialized = true;
            lastConnected = connected;
            if (!connected) {
                NotificationToastManager.showWarning("Client is not connected.", true);
            }
            return;
        }
        if (connected != lastConnected) {
            if (connected) {
                NotificationToastManager.showInfo("Reconnected to the server.");
                attemptAutoLogin();
            } else {
                autoLoginInProgress.set(false);
                NotificationToastManager.showWarning("Disconnected from the server.", true);
            }
            lastConnected = connected;
        }
    }

    private void attemptAutoLogin() {
        AuthService authService = AuthService.getInstance();
        if (!ClientSession.getInstance().isAuthenticated() || !authService.hasCachedCredentials()) {
            return;
        }
        if (!autoLoginInProgress.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture<Message> loginFuture = authService.autoLogin();
        loginFuture.thenAccept(message -> Platform.runLater(() -> handleAutoLoginResponse(message)))
                .exceptionally(throwable -> {
                    autoLoginInProgress.set(false);
                    NotificationToastManager.showWarning("Reconnected, but automatic login failed.");
                    Platform.runLater(() -> SceneManager.resetAndSwitchScene("LoginMenu.fxml"));
                    return null;
                });
    }

    private void handleAutoLoginResponse(Message message) {
        autoLoginInProgress.set(false);
        if (message instanceof LoginResultMessage result) {
            if (message.getType() == MessageType.LOGIN_SUCCESS) {
                ClientSession.getInstance().login(result.getUserId(), result.getUsername(), result.getRole());
                return;
            }
            if (message.getType() == MessageType.LOGIN_FAILURE) {
                AuthService.getInstance().clearSessionAndCredentials();
                NotificationToastManager.showWarning("Reconnected, but automatic login failed.");
                Platform.runLater(() -> SceneManager.resetAndSwitchScene("LoginMenu.fxml"));
                return;
            }
        }
        AuthService.getInstance().clearSessionAndCredentials();
        NotificationToastManager.showWarning("Reconnected, but automatic login failed.");
        Platform.runLater(() -> SceneManager.resetAndSwitchScene("LoginMenu.fxml"));
    }
}
