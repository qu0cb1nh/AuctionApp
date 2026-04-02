package net.auctionapp.client.controllers;


import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;

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
        // Read input values.
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        // Ensure all fields are filled.
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            statusLabel.setText("Please fill in all the information.");
            return;
        }

        // Verify the confirmation password.
        if (!password.equals(confirmPassword)) {
            statusLabel.setText("Verification password does not match.");
            return;
        }

        // Inputs look valid.
        statusLabel.setText("Registering account...");
        ClientApp.getInstance().getNetworkService().sendMessage(new RegisterRequestMessage(username, password));

        // Optional: navigate back to the login screen after registration.
    }

    private void handleServerMessage(Message message) {
        if (message == null) {
            return;
        }
        if (message.getType() != MessageType.REGISTER_SUCCESS && message.getType() != MessageType.REGISTER_FAILURE) {
            return;
        }
        RegisterResultMessage result = (RegisterResultMessage) message;
        Platform.runLater(() -> statusLabel.setText(result.getMessage()));
    }

    @FXML
    public void switchToLogin(MouseEvent event) {
        if (messageListener != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.REGISTER_SUCCESS, messageListener);
            ClientApp.getInstance().removeMessageHandler(MessageType.REGISTER_FAILURE, messageListener);
        }
        SceneNavigator.switchScene((Node) event.getSource(),
                "/net/auctionapp/client/views/LoginMenu.fxml");
    }
}
