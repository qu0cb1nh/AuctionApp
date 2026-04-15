package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

public class MainMenuController implements Initializable {
    @FXML
    private Label usernameLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label statusLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String username = ClientApp.getInstance() != null ? ClientApp.getInstance().getCurrentUsername() : null;
        String role = ClientApp.getInstance() != null ? ClientApp.getInstance().getCurrentRole() : null;

        usernameLabel.setText((username == null || username.isBlank()) ? "Guest" : username);
        roleLabel.setText("Role: " + formatRole(role));
        balanceLabel.setText("Balance: $0.00");
        statusLabel.setText("Ready.");
    }

    @FXML
    public void handleBrowseAuctions(ActionEvent event) {
        SceneNavigator.switchScene("views/AuctionList.fxml");
    }

    @FXML
    public void handleSellItem(ActionEvent event) {
        SceneNavigator.switchScene("views/CreateAuction.fxml");
    }

    @FXML
    public void handleMyBids(ActionEvent event) {
        SceneNavigator.switchScene("views/MyBids.fxml");
    }

    @FXML
    public void handleMyListings(ActionEvent event) {
        SceneNavigator.switchScene("views/MyListings.fxml");
    }

    @FXML
    public void handleSettings(ActionEvent event) {
        SceneNavigator.switchScene("views/Settings.fxml");
    }

    @FXML
    public void handleNotifications(ActionEvent event) {
        SceneNavigator.switchScene("views/Notifications.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        if (ClientApp.getInstance() != null) {
            ClientApp.getInstance().setCurrentUser(null, null);
        }
        SceneNavigator.switchScene("views/LoginMenu.fxml");
    }

    private String formatRole(String role) {
        if (role == null || role.isBlank()) {
            return "UNKNOWN";
        }
        return role.toUpperCase(Locale.ROOT);
    }
}
