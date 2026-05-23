package net.auctionapp.client.ui.controllers;

import javafx.fxml.FXML;
import net.auctionapp.client.AppLifecycleManager;

public class DisconnectedMenuController {
    @FXML
    private void handleRetryConnection() {
        AppLifecycleManager.getInstance().retryConnection();
    }
}
