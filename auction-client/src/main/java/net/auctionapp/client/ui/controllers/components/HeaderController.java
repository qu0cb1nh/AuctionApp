package net.auctionapp.client.ui.controllers.components;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.SceneManager;

public class HeaderController {
    @FXML
    private Label titleLabel;
    @FXML
    private MenuButton userMenuButton;
    @FXML
    private MenuItem adminPanelMenuItem;

    private ContextMenu userContextMenu;

    @FXML
    public void initialize() {
        ClientSession session = ClientSession.getInstance();
        String username = session.getUsername();
        userMenuButton.setText((username == null || username.isBlank()) ? "Guest" : username);
        configureUserMenuPopup();
        adminPanelMenuItem.setVisible(session.isAdmin());
        adminPanelMenuItem.setDisable(!session.isAdmin());
    }

    public void setupHeader(String title) {
        titleLabel.setText((title == null || title.isBlank()) ? "Auction App" : title);
    }

    @FXML
    public void handleBack() {
        SceneManager.goBack();
    }

    @FXML
    public void handleOpenDashboard() {
        SceneManager.switchScene("DashboardMenu.fxml");
    }

    @FXML
    public void handleOpenNotifications() {
        SceneManager.switchScene("NotificationsMenu.fxml");
    }

    @FXML
    public void handleOpenWallet() {
        SceneManager.switchScene("WalletMenu.fxml");
    }

    @FXML
    public void handleOpenActivity() {
        SceneManager.switchScene("MyActivityMenu.fxml");
    }

    @FXML
    public void handleOpenPurchases() {
        SceneManager.switchScene("PurchasesMenu.fxml");
    }

    @FXML
    public void handleOpenAdminPanel() {
        SceneManager.switchScene("AdminPanelMenu.fxml");
    }

    @FXML
    public void handleLogout() {
        AuthService.getInstance().clearSessionAndCredentials();
        SceneManager.resetAndSwitchScene("LoginMenu.fxml");
    }

    private void configureUserMenuPopup() {
        userContextMenu = new ContextMenu();
        userContextMenu.getItems().setAll(userMenuButton.getItems());
        userMenuButton.getItems().clear();
        userMenuButton.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (userContextMenu.isShowing()) {
                userContextMenu.hide();
            } else {
                showUserContextMenu();
            }
            event.consume();
        });
    }

    private void showUserContextMenu() {
        Bounds buttonBounds = userMenuButton.localToScreen(userMenuButton.getBoundsInLocal());
        if (buttonBounds == null) {
            return;
        }
        userContextMenu.show(userMenuButton, buttonBounds.getMaxX(), buttonBounds.getMaxY());
        alignUserContextMenu(buttonBounds);
        Platform.runLater(() -> alignUserContextMenu(buttonBounds));
    }

    private void alignUserContextMenu(Bounds buttonBounds) {
        double popupWidth = userContextMenu.getWidth();
        if (popupWidth <= 0) {
            return;
        }
        userContextMenu.setX(buttonBounds.getMaxX() - popupWidth);
    }
}
