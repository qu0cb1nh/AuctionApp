package net.auctionapp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.utils.ConfigUtil;

import java.io.IOException;

public class ClientApp extends Application {
    private static ClientApp instance;
    private final NetworkService networkService;

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
        networkService.addMessageListener(this::processMessage);
    }

    /**
     * Processes messages received from the server.
     * @param message The message object deserialized from JSON.
     */
    private void processMessage(Message message) {
        if (message == null) {
            return;
        }

        switch (message.getType()) {
            case PRICE_UPDATE:
                PriceUpdateMessage priceUpdate = (PriceUpdateMessage) message;
                System.out.printf("[UPDATE] Item %s has a new price: %.2f (led by %s)%n",
                        priceUpdate.getItemId(), priceUpdate.getNewPrice(), priceUpdate.getLeadingUserName());
                break;
            case LOGIN_SUCCESS:
            case LOGIN_FAILURE:
                LoginResultMessage loginResult = (LoginResultMessage) message;
                System.out.println("[AUTH] " + loginResult.getMessage());
                break;
            case ERROR:
                ErrorMessage error = (ErrorMessage) message;
                System.err.println("[ERROR] " + error.getErrorMessage());
                break;
            default:
                System.out.println("[INFO] Received an unhandled message type: " + message.getType());
                break;
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        networkService.shutdown();
    }
}
