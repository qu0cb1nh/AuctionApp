package net.auctionapp.client.controllers;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.auctionapp.client.SceneNavigator;

import java.io.IOException;

public class AuctionListController {

    /**
     * Hàm này được kích hoạt khi người dùng bấm nút "Bid now!"
     * Mục đích: Chuyển màn hình từ Danh sách sang Chi tiết sản phẩm.
     */
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