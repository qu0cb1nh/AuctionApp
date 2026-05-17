package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.ui.managers.SceneManager;

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

        String username = AuthService.getInstance().getCurrentUsername();

        usernameLabel.setText("Welcome back, " + (username == null || username.isBlank() ? "Guest" : username));
    }

    @FXML
    public void handleBrowseAuctions(ActionEvent event) {
        SceneManager.switchScene("AuctionListMenu.fxml");
    }

    @FXML
    public void handleSellItem(ActionEvent event) {
        SceneManager.switchScene("CreateAuctionMenu.fxml");
    }
}
