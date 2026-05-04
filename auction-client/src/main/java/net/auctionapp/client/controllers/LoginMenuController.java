package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.ui.SceneManager;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.utils.CredentialUtil;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginMenuController implements Initializable {
    private final AuthService authService = AuthService.getInstance();

    @FXML
    private Button loginButton;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private AssistantPanelController assistantPanelController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // No persistent message listeners are required for login request/response flow.
    }

    @FXML
    protected void onLoginButtonClicked(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        try {
            CredentialUtil.validateLogin(username, password);
        } catch (ValidationException e) {
            assistantPanelController.speak(e.getMessage(), "#e67e22");
            return;
        }

        authService.login(username, password, this::handleServerResponse);
    }

    private void handleServerResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            assistantPanelController.speak(errorMessage.getErrorMessage(), "#e74c3c");
            return;
        }
        if (!(message instanceof LoginResultMessage result)) {
            assistantPanelController.speak("Unexpected response from server.", "#e74c3c");
            return;
        }

        if (message.getType() == MessageType.LOGIN_SUCCESS) {
            assistantPanelController.speak(result.getMessage(), "#27ae60");

            AuthService.getInstance().setCurrentUser(result.getUserId(), result.getUsername(), result.getRole());
            SceneManager.switchSceneWithDelay("MainMenu", 1500);

        } else if (message.getType() == MessageType.LOGIN_FAILURE) {
            assistantPanelController.speak(result.getMessage(), "#e74c3c");
        }
    }

    @FXML
    public void switchToRegister(MouseEvent event) {
        SceneManager.switchScene("RegisterMenu");
    }
}
