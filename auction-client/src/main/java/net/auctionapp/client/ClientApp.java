package net.auctionapp.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.client.utils.NotificationToastUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.NotificationMessage;
import net.auctionapp.common.notifications.NotificationView;
import net.auctionapp.common.utils.ConfigUtil;
import net.auctionapp.common.utils.UserIdentityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ClientApp extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientApp.class.getName());
    public static final double WINDOW_WIDTH = 1067;
    public static final double WINDOW_HEIGHT = 700;
    private static ClientApp instance;
    private static Stage primaryStage;
    private final NetworkService networkService;
    private String currentUserId;
    private String currentUsername;
    private String currentRole;
    private String selectedAuctionId;
    private Consumer<Message> notificationPushHandler;

    public ClientApp() {
        instance = this;
        networkService = new NetworkService();
    }

    public static ClientApp getInstance() {
        return instance;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public NetworkService getNetworkService() {
        return networkService;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentUser(String userId, String username, String role) {
        this.currentUserId = userId;
        this.currentUsername = username;
        this.currentRole = role;
    }

    public String getSelectedAuctionId() {
        return selectedAuctionId;
    }

    public void setSelectedAuctionId(String selectedAuctionId) {
        this.selectedAuctionId = selectedAuctionId;
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        connectToServer();

        FXMLLoader loader = new FXMLLoader(ClientApp.class.getResource("views/LoginMenu.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(NotificationToastUtil.wrapWithNotificationHost(root), WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setTitle("Auction App");
        primaryStage.setScene(scene);
        primaryStage.setWidth(WINDOW_WIDTH);
        primaryStage.setHeight(WINDOW_HEIGHT);
        primaryStage.setMinWidth(WINDOW_WIDTH);
        primaryStage.setMaxWidth(WINDOW_WIDTH);
        primaryStage.setMinHeight(WINDOW_HEIGHT);
        primaryStage.setMaxHeight(WINDOW_HEIGHT);
        primaryStage.setResizable(false);
        primaryStage.show();

        registerGlobalMessageHandlers();
    }

    public void addMessageHandler(MessageType type, Consumer<Message> handler) {
        networkService.addMessageHandler(type, handler);
    }

    public void removeMessageHandler(MessageType type, Consumer<Message> handler) {
        networkService.removeMessageHandler(type, handler);
    }

    public CompletableFuture<Message> sendRequest(Message request) {
        return networkService.sendRequest(request);
    }

    public CompletableFuture<Message> sendRequest(Message request, Duration timeout) {
        return networkService.sendRequest(request, timeout);
    }

    public void sendRequest(Message request, BiConsumer<Message, Throwable> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        networkService.sendRequest(request)
                .whenComplete((response, throwable) -> Platform.runLater(() -> callback.accept(response, throwable)));
    }

    private void connectToServer() {
        String host = ConfigUtil.getServerHost();
        int port = ConfigUtil.getServerPort();
        networkService.connect(host, port);
    }

    @Override
    public void stop() throws Exception {
        if (notificationPushHandler != null) {
            removeMessageHandler(MessageType.NOTIFICATION, notificationPushHandler);
            notificationPushHandler = null;
        }
        super.stop();
        networkService.shutdown();
    }

    private void registerGlobalMessageHandlers() {
        notificationPushHandler = this::handleGlobalNotificationPush;
        addMessageHandler(MessageType.NOTIFICATION, notificationPushHandler);
    }

    private void handleGlobalNotificationPush(Message message) {
        if (!(message instanceof NotificationMessage notificationMessage)) {
            return;
        }
        NotificationView notification = notificationMessage.getNotification();
        if (notification == null || !isNotificationForCurrentUser(notificationMessage)) {
            return;
        }
        Platform.runLater(() ->  NotificationToastUtil.show(notification));
    }

    private boolean isNotificationForCurrentUser(NotificationMessage notificationMessage) {
        if (notificationMessage == null || notificationMessage.getNotification() == null) {
            return false;
        }
        NotificationView notification = notificationMessage.getNotification();
        String recipientUserId = UserIdentityUtil.normalizeUserId(notificationMessage.getRecipientUserId());
        String notificationUserId = UserIdentityUtil.normalizeUserId(notification.getUserId());
        String normalizedCurrentUserId = UserIdentityUtil.normalizeUserId(getCurrentUserId());
        if (normalizedCurrentUserId.isEmpty()) {
            LOGGER.info("Popup notification skipped: missing current user id. notificationId=" + notification.getId());
            return false;
        }
        if (recipientUserId.isEmpty() && notificationUserId.isEmpty()) {
            LOGGER.info("Popup notification skipped: missing recipient identity. currentUserId="
                    + normalizedCurrentUserId + ", notificationId=" + notification.getId());
            return false;
        }
        if (!recipientUserId.isEmpty() && !recipientUserId.equals(normalizedCurrentUserId)) {
            LOGGER.info("Popup notification skipped: recipient mismatch. recipientUserId="
                    + recipientUserId + ", currentUserId=" + normalizedCurrentUserId
                    + ", notificationId=" + notification.getId());
            return false;
        }
        if (!notificationUserId.isEmpty() && !notificationUserId.equals(normalizedCurrentUserId)) {
            LOGGER.info("Popup notification skipped: payload user mismatch. payloadUserId="
                    + notificationUserId + ", currentUserId=" + normalizedCurrentUserId
                    + ", notificationId=" + notification.getId());
            return false;
        }
        LOGGER.info("Popup notification accepted. recipientUserId=" + recipientUserId
                + ", payloadUserId=" + notificationUserId
                + ", currentUserId=" + normalizedCurrentUserId
                + ", notificationId=" + notification.getId());
        return true;
    }
}
