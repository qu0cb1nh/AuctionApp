package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;

public class HeaderController {
    @FXML
    private Button backButton;
    @FXML
    private Label titleLabel;
    @FXML
    private MenuButton userMenuButton;

    private String backTargetFxml = "MainMenu";

    @FXML
    public void initialize() {
        String username = ClientApp.getInstance() != null ? ClientApp.getInstance().getCurrentUsername() : null;
        userMenuButton.setText((username == null || username.isBlank()) ? "Guest" : username);
    }

    public void setupHeader(String title, boolean showBackButton, String backTarget) {
        titleLabel.setText((title == null || title.isBlank()) ? "Auction App" : title);
        setBackButtonVisible(showBackButton);
        if (backTarget != null && !backTarget.isBlank()) {
            backTargetFxml = backTarget;
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        SceneNavigator.switchScene(backTargetFxml);
    }

    @FXML
    public void handleOpenNotifications(ActionEvent event) {
        SceneNavigator.switchScene("Notifications");
    }

    @FXML
    public void handleOpenSettings(ActionEvent event) {
        SceneNavigator.switchScene("Settings");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        if (ClientApp.getInstance() != null) {
            ClientApp.getInstance().setCurrentUser(null, null);
        }
        SceneNavigator.switchScene("LoginMenu");
    }

    private void setBackButtonVisible(boolean visible) {
        backButton.setVisible(visible);
        backButton.setDisable(!visible);
    }
}
