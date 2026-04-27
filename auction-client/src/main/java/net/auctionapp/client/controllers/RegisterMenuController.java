package net.auctionapp.client.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;
import net.auctionapp.common.utils.CredentialUtil;

import java.net.URL;
import java.util.ResourceBundle;

public class RegisterMenuController implements Initializable {

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
    private AssistantPanelController assistantPanelController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // No persistent message handlers are required for register request/response flow.
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
            assistantPanelController.speak(e.getMessage(), "#e67e22");
            return;
        }
        ClientApp.getInstance().sendRequest(new RegisterRequestMessage(username, password), this::handleServerResponse);
    }

    private void handleServerResponse(Message message, Throwable throwable) {
        if (throwable != null) {
            assistantPanelController.speak("Registration failed: " + throwable.getMessage(), "#e74c3c");
            return;
        }
        if (message instanceof ErrorMessage errorMessage) {
            assistantPanelController.speak(errorMessage.getErrorMessage(), "#e74c3c");
            return;
        }
        if (!(message instanceof RegisterResultMessage result)) {
            assistantPanelController.speak("Unexpected response from server.", "#e74c3c");
            return;
        }

        if (message.getType() == MessageType.REGISTER_SUCCESS) {
            assistantPanelController.speak(result.getMessage(), "#27ae60");
            SceneNavigator.switchSceneWithDelay("LoginMenu", 1500);
        } else if (message.getType() == MessageType.REGISTER_FAILURE) {
            assistantPanelController.speak(result.getMessage(), "#e74c3c");
        }
    }

    @FXML
    public void switchToLogin(MouseEvent event) {
        SceneNavigator.switchScene("LoginMenu");
    }
}
