package net.auctionapp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.utils.ConfigUtil;

import java.io.IOException;
import java.util.function.Consumer;

public class ClientApp extends Application {
    private static ClientApp instance;
    private static Stage primaryStage;
    private final NetworkService networkService;

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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        connectToServer();

        FXMLLoader loader = new FXMLLoader(ClientApp.class.getResource("views/LoginMenu.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("Auction App");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public void addMessageHandler(MessageType type, Consumer<Message> handler) {
        networkService.addMessageHandler(type, handler);
    }

    public void removeMessageHandler(MessageType type, Consumer<Message> handler) {
        networkService.removeMessageHandler(type, handler);
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
