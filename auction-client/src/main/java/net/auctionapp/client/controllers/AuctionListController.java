package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import net.auctionapp.client.SceneNavigator;

public class AuctionListController {
    @FXML
    private HeaderController appHeaderController;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Explore Auctions", true, "MainMenu");
    }

    @FXML
    public void handleViewItem(ActionEvent event) {
        SceneNavigator.switchScene("AuctionItem");
    }

    @FXML
    public void handleSignOut(ActionEvent event) {
        SceneNavigator.switchScene("LoginMenu");
    }

    @FXML
    public void handleBackToMainMenu(ActionEvent event) {
        SceneNavigator.switchScene("MainMenu");
    }
}
