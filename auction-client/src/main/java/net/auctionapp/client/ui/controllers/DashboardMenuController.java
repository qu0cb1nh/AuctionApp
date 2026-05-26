package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.FxViewUtil;


public class DashboardMenuController {
    @FXML
    private HeaderController appHeaderController;
    @FXML
    private Label usernameLabel;
    @FXML
    private Button sellItemButton;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Dashboard");

        ClientSession session = ClientSession.getInstance();
        String username = session.getUsername();

        usernameLabel.setText("Welcome back, " + (username == null || username.isBlank() ? "Guest" : username));
        boolean canCreateAuction = session.canCreateAuction();
        FxViewUtil.setVisible(sellItemButton, canCreateAuction);
        sellItemButton.setDisable(!canCreateAuction);
    }

    @FXML
    public void handleBrowseAuctions() {
        SceneManager.switchScene("AuctionListMenu.fxml");
    }

    @FXML
    public void handleSellItem() {
        if (!ClientSession.getInstance().canCreateAuction()) {
            return;
        }
        SceneManager.switchScene("CreateAuctionMenu.fxml");
    }
}
