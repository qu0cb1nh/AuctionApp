package net.auctionapp.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.utils.ConfigUtil;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ClientApp extends Application {
    public static final double WINDOW_WIDTH = 1067;
    public static final double WINDOW_HEIGHT = 700;
    private static ClientApp instance;
    private static Stage primaryStage;
    private final NetworkService networkService;
    private String currentUsername;
    private String currentRole;
    private String selectedAuctionId;

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

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentUser(String username, String role) {
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
        Scene scene = new Scene(loader.load(), WINDOW_WIDTH, WINDOW_HEIGHT);
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
        super.stop();
        networkService.shutdown();
    }
}
