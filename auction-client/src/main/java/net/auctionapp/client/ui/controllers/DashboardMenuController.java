package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.SceneManager;

import java.net.URL;
import java.util.ResourceBundle;

public class DashboardMenuController implements Initializable {
    @FXML
    private HeaderController appHeaderController;
    @FXML
    private Label usernameLabel;
    @FXML
    private Button sellItemButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Dashboard", true);

        ClientSession session = ClientSession.getInstance();
        String username = session.getUsername();

        usernameLabel.setText("Welcome back, " + (username == null || username.isBlank() ? "Guest" : username));
        setButtonVisible(sellItemButton, session.canCreateAuction());
    }

    @FXML
    public void handleBrowseAuctions(ActionEvent event) {
        SceneManager.switchScene("AuctionListMenu.fxml");
    }

    @FXML
    public void handleSellItem(ActionEvent event) {
        if (!ClientSession.getInstance().canCreateAuction()) {
            return;
        }
        SceneManager.switchScene("CreateAuctionMenu.fxml");
    }

    private void setButtonVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
        button.setDisable(!visible);
    }
}
