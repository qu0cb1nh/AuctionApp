package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.ui.managers.SceneManager;

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
        String username = AuthService.getInstance().getCurrentUsername();
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
        SceneManager.switchScene(backTargetFxml);
    }

    @FXML
    public void handleOpenNotifications(ActionEvent event) {
        SceneManager.switchScene("Notifications");
    }

    @FXML
    public void handleOpenActivity(ActionEvent event) {
        SceneManager.switchScene("MyActivity");
    }

    @FXML
    public void handleOpenPurchases(ActionEvent event) {
        SceneManager.switchScene("Purchases");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        AuthService.getInstance().setCurrentUser(null, null, null);
        SceneManager.switchScene("LoginMenu");
    }

    private void setBackButtonVisible(boolean visible) {
        backButton.setVisible(visible);
        backButton.setDisable(!visible);
    }
}
