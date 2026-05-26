package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.AssistantPanelController;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auth.RegisterResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.utils.CredentialUtil;

public class RegisterMenuController {
    private final AuthService authService = AuthService.getInstance();

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private AssistantPanelController assistantPanelController;

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
    public void switchToLogin() {
        SceneManager.resetAndSwitchScene("LoginMenu.fxml");
    }
}
