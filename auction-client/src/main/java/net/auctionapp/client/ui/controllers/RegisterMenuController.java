package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.AssistantPanelController;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auth.RegisterResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.utils.CredentialUtil;

import java.net.URL;
import java.util.ResourceBundle;

public class RegisterMenuController implements Initializable {
    private final AuthService authService = AuthService.getInstance();

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
        // No persistent message listeners are required for register request/response flow.
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
            assistantPanelController.speak(e.getMessage(), AssistantPanelController.Tone.WARNING);
            return;
        }
        authService.register(username, password, this::handleServerResponse);
    }

    private void handleServerResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            assistantPanelController.speak(errorMessage.getErrorMessage(), AssistantPanelController.Tone.ERROR);
            return;
        }
        if (!(message instanceof RegisterResponseMessage result)) {
            assistantPanelController.speak("Unexpected response from server.", AssistantPanelController.Tone.ERROR);
            return;
        }

        if (message.getType() == MessageType.REGISTER_SUCCESS) {
            assistantPanelController.speak(result.getMessage(), AssistantPanelController.Tone.SUCCESS);
            SceneManager.resetAndSwitchSceneWithDelay("LoginMenu.fxml", 1500);
        } else if (message.getType() == MessageType.REGISTER_FAILURE) {
            assistantPanelController.speak(result.getMessage(), AssistantPanelController.Tone.ERROR);
        } else {
            assistantPanelController.speak("Unexpected response from server.", AssistantPanelController.Tone.ERROR);
        }
    }

    @FXML
    public void switchToLogin(MouseEvent event) {
        SceneManager.resetAndSwitchScene("LoginMenu.fxml");
    }
}
