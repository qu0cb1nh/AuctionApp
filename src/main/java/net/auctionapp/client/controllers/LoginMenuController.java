package net.auctionapp.client.controllers;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.NetworkService;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginMenuController implements Initializable {

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        ClientApp.getInstance().getNetworkService().addMessageListener(this::handleServerMessage);
    }

    @FXML
    private Button loginButton;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label statusLabel;

    @FXML
    protected void onLoginButtonClicked(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        statusLabel.setText("Signing in...");
        ClientApp.getInstance().getNetworkService().sendMessage(new LoginRequestMessage(username, password));
    }

    private void handleServerMessage(Message message) {
        if (message == null) {
            return;
        }
        if (message.getType() != MessageType.LOGIN_SUCCESS && message.getType() != MessageType.LOGIN_FAILURE) {
            return;
        }
        LoginResultMessage result = (LoginResultMessage) message;
        Platform.runLater(() -> statusLabel.setText(result.getMessage()));
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
}
