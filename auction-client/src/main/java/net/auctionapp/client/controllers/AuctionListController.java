package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import net.auctionapp.client.SceneNavigator;

public class AuctionListController {
    @FXML
    private HeaderController appHeaderController;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Explore Auctions", true, "views/MainMenu.fxml");
    }

    @FXML
    public void handleViewItem(ActionEvent event) {
        SceneNavigator.switchScene("views/AuctionItem.fxml");
    }

    @FXML
    public void handleSignOut(ActionEvent event) {
        SceneNavigator.switchScene("views/LoginMenu.fxml");
    }

    @FXML
    public void handleBackToMainMenu(ActionEvent event) {
        SceneNavigator.switchScene("views/MainMenu.fxml");
    }
}
