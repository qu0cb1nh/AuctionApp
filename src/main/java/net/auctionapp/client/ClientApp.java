package net.auctionapp.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import net.auctionapp.common.messages.*;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.utils.ConfigReader;
import net.auctionapp.common.utils.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientApp extends Application {

    private PrintWriter out;
    private Socket socket;

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
        try {
            String host = ConfigReader.getServerHost();
            int port = ConfigReader.getServerPort();

            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // TODO: Connection success notification

            Thread listenerThread = new Thread(() -> listenForServerMessages(in));
            listenerThread.setDaemon(true);
            listenerThread.start();

        } catch (IOException e) {
            // TODO: Connection error notification
        }
    }

    private void listenForServerMessages(BufferedReader in) {
        try {
            String jsonString;
            while ((jsonString = in.readLine()) != null) {
                final Message message = JsonUtil.fromJson(jsonString);
                Platform.runLater(() -> processMessage(message));
            }
        } catch (IOException e) {
            // TODO: Lost connection notification
        }
    }

    /**
     * Processes messages received from the server.
     * @param message The message object deserialized from JSON.
     */
    private void processMessage(Message message) {
        String displayText = "";
        if (message == null) {
            displayText = "[INFO] Received an unknown message.";
        } else {
            switch (message.getType()) {
                case PRICE_UPDATE:
                    PriceUpdateMessage priceUpdate = (PriceUpdateMessage) message;
                    displayText = String.format("[UPDATE] Item %s has a new price: %.2f (led by %s)",
                            priceUpdate.getItemId(), priceUpdate.getNewPrice(), priceUpdate.getLeadingUserName());
                    break;
                case ERROR:
                    ErrorMessage error = (ErrorMessage) message;
                    displayText = "[ERROR] " + error.getErrorMessage();
                    break;
                default:
                    displayText = "[INFO] Received an unhandled message type.";
                    break;
            }
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
