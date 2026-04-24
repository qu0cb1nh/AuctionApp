package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;

import java.net.URL;
import java.util.ResourceBundle;

public class MainMenuController implements Initializable {
    @FXML
    private HeaderController appHeaderController;
    @FXML
    private Label usernameLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Dashboard", false, null);

        String username = ClientApp.getInstance() != null ? ClientApp.getInstance().getCurrentUsername() : null;

        usernameLabel.setText("Welcome back, " + (username == null || username.isBlank() ? "Guest" : username));
    }

    @FXML
    public void handleBrowseAuctions(ActionEvent event) {
        SceneNavigator.switchScene("AuctionList");
    }

    @FXML
    public void handleSellItem(ActionEvent event) {
        SceneNavigator.switchScene("CreateAuction");
    }
}
