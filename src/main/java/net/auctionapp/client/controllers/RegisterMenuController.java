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
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class RegisterMenuController implements Initializable {

    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern VALID_PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*=+]+$");

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

        // Validate username length.
        if (username.length() < 3) {
            statusLabel.setText("Username must be at least 3 characters");
            return;
        }
        if (username.length() > 20) {
            statusLabel.setText("Username must not exceed 20 characters");
            return;
        }

        // Validate username starts with a letter.
        if (!Character.isLetter(username.charAt(0))) {
            statusLabel.setText("Username must start with a letter.");
            return;
        }

        // Validate username characters.
        if (!VALID_USERNAME_PATTERN.matcher(username).matches()) {
            statusLabel.setText("Username can not contain invalid characters.");
            return;
        }

        // Verify the confirmation password.
        if (!password.equals(confirmPassword)) {
            statusLabel.setText("Verification password does not match.");
            return;
        }

        // Validate password length.
        if (password.length() < 6) {
            statusLabel.setText("Password must be at least 6 characters.");
            return;
        }
        if (password.length() > 64) {
            statusLabel.setText("Password must not exceed 64 characters.");
            return;
        }

        // Validate password characters.
        if (!VALID_PASSWORD_PATTERN.matcher(password).matches()) {
            statusLabel.setText("Password can not contain invalid characters.");
            return;
        }

        // Inputs look valid.
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
