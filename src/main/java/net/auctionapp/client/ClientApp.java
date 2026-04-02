package net.auctionapp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.utils.ConfigUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ClientApp extends Application {
    private static ClientApp instance;
    private final NetworkService networkService;
    private final Map<MessageType, List<Consumer<Message>>> messageHandlers  = new ConcurrentHashMap<>();

    public ClientApp() {
        instance = this;
        networkService = new NetworkService();
    }

    public static ClientApp getInstance() {
        return instance;
    }

    public NetworkService getNetworkService() {
        return networkService;
    }

    public void addMessageHandler(MessageType type, Consumer<Message> handler) {
        if (type == null || handler == null) {
            return;
        }
        messageHandlers
                .computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(handler);
    }

    public void removeMessageHandler(MessageType type, Consumer<Message> handler) {
        if (type == null || handler == null) {
            return;
        }
        List<Consumer<Message>> handlers = messageHandlers.get(type);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        connectToServer();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("views/LoginMenu.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("Auction App");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void connectToServer() {
        String host = ConfigUtil.getServerHost();
        int port = ConfigUtil.getServerPort();
        networkService.connect(host, port);
        networkService.setMessageListener(this::processMessage);
    }

    /**
     * Processes messages received from the server.
     * @param message The message object deserialized from JSON.
     */
    private void processMessage(Message message) {
        if (message == null) {
            return;
        }
        dispatchToHandlers(message);
    }

    private void dispatchToHandlers(Message message) {
        List<Consumer<Message>> handlers = messageHandlers.get(message.getType());
        if (handlers == null) {
            return;
        }
        for (Consumer<Message> handler : handlers) {
            handler.accept(message);
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        networkService.shutdown();
    }
}
