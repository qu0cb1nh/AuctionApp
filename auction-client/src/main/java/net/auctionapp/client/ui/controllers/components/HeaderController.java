package net.auctionapp.client.ui.controllers.components;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.users.UserRole;

public class HeaderController {
    @FXML
    private Button backButton;
    @FXML
    private Label titleLabel;
    @FXML
    private MenuButton userMenuButton;
    @FXML
    private MenuItem adminPanelMenuItem;

    private String backTargetFxml = "MainMenu.fxml";

    @FXML
    public void initialize() {
        String username = AuthService.getInstance().getCurrentUsername();
        userMenuButton.setText((username == null || username.isBlank()) ? "Guest" : username);
        boolean isAdmin = AuthService.getInstance().getCurrentUserRole() == UserRole.ADMIN;
        adminPanelMenuItem.setVisible(isAdmin);
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
        SceneManager.switchScene("NotificationsMenu.fxml");
    }

    @FXML
    public void handleOpenWallet(ActionEvent event) {
        SceneManager.switchScene("WalletMenu.fxml");
    }

    @FXML
    public void handleOpenActivity(ActionEvent event) {
        SceneManager.switchScene("MyActivityMenu.fxml");
    }

    @FXML
    public void handleOpenPurchases(ActionEvent event) {
        SceneManager.switchScene("PurchasesMenu.fxml");
    }

    @FXML
    public void handleOpenAdminPanel(ActionEvent event) {
        SceneManager.switchScene("AdminPanelMenu.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        AuthService.getInstance().setCurrentUser(null, null, null);
        SceneManager.switchScene("LoginMenu.fxml");
    }

    private void setBackButtonVisible(boolean visible) {
        backButton.setVisible(visible);
        backButton.setDisable(!visible);
    }
}
