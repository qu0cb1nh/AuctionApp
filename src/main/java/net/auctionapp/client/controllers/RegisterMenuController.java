package net.auctionapp.client.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;
import net.auctionapp.common.utils.CredentialUtil;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class RegisterMenuController implements Initializable {

    private Consumer<Message> messageListener;

    // Fields for the registration form.
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button registerButton;

    @FXML
    private Label statusLabel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageListener = this::handleServerMessage;
        ClientApp.getInstance().addMessageHandler(MessageType.REGISTER_SUCCESS, messageListener);
        ClientApp.getInstance().addMessageHandler(MessageType.REGISTER_FAILURE, messageListener);
    }

    // Triggered when the user clicks "Register".
    @FXML
    public void handleRegister() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        try {
            CredentialUtil.validateRegistration(username, password, confirmPassword);
        } catch (ValidationException e) {
            statusLabel.setText(e.getMessage());
            return;
        }

        statusLabel.setText("Registering account...");
        ClientApp.getInstance().getNetworkService().sendMessage(new RegisterRequestMessage(username, password));
    }

    private void handleServerMessage(Message message) {
        if (!(message instanceof RegisterResultMessage result)) {
            return;
        }
        statusLabel.setText(result.getMessage());

        if (message.getType() == MessageType.REGISTER_SUCCESS) {
            cleanupHandlers();
            SceneNavigator.switchSceneWithDelay("views/LoginMenu.fxml", 1500);
        }
    }

    private void cleanupHandlers() {
        if (messageListener != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.REGISTER_SUCCESS, messageListener);
            ClientApp.getInstance().removeMessageHandler(MessageType.REGISTER_FAILURE, messageListener);
        }
    }

    @FXML
    public void switchToLogin(MouseEvent event) {
        cleanupHandlers();
        SceneNavigator.switchScene("views/LoginMenu.fxml");
    }
}
