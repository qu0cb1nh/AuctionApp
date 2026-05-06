package net.auctionapp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.client.ui.managers.NotificationToastManager;

import java.io.IOException;

public class ClientApp extends Application {
    public static final double WINDOW_WIDTH = 1067;
    public static final double WINDOW_HEIGHT = 700;
    private static Stage primaryStage;
    private String selectedAuctionId;

    public String getSelectedAuctionId() {
        return selectedAuctionId;
    }

    public void setSelectedAuctionId(String selectedAuctionId) {
        this.selectedAuctionId = selectedAuctionId;
    }

    private static final ClientApp INSTANCE = new ClientApp();

    public static ClientApp getInstance() {
        return INSTANCE;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        AppLifecycleManager.getInstance().start();

        FXMLLoader loader = new FXMLLoader(ClientApp.class.getResource("/net/auctionapp/client/ui/fxml/LoginMenu.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(NotificationToastManager.wrapWithNotificationHost(root), WINDOW_WIDTH, WINDOW_HEIGHT);
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

    @Override
    public void stop() throws Exception {
        super.stop();
        AppLifecycleManager.getInstance().shutdown();
    }
}
