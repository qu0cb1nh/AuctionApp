package net.auctionapp.client.controllers;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import java.io.IOException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginMenuController implements Initializable {
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
    }

    @FXML
    private Button loginButton;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField passwordField;
    @FXML
    private Label statusLabel;

    @FXML
    protected void onLoginButtonClicked(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        System.out.println(usernameField.getText() + " - " + passwordField.getText());
    }
    @FXML
    public void switchToRegister(MouseEvent event) {
        try {
            System.out.println("Start moving to the Registration screen....");

            // Load file FXML mới
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/net/auctionapp/client/views/RegisterMenu.fxml"));
            Parent root = loader.load();

            // Lấy sân khấu (Stage) hiện tại
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            // Thay cảnh (Scene) mới vào
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.centerOnScreen(); // Tự động căn giữa màn hình cho đẹp
            stage.show();

            System.out.println("Screen switching successful!");
        } catch (Exception e) {
            System.err.println("ERROR: Unable to switch screens!");
            e.printStackTrace(); // Dòng này sẽ in ra chi tiết lỗi ở tab Run
        }
    }
    @FXML
    public void handleForgotPassword() {
        System.out.println("The user clicked \"Forgot password!\"");
        // Sau này code xử lý lấy lại mật khẩu sẽ nằm ở đây
    }

}
