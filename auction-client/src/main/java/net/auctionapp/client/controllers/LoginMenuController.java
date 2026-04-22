package net.auctionapp.client.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.utils.CredentialUtil;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class LoginMenuController implements Initializable {

    private Consumer<Message> messageListener;

    @FXML
    private Button loginButton;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label chatBubble;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        messageListener = this::handleServerMessage;
        ClientApp.getInstance().addMessageHandler(MessageType.LOGIN_SUCCESS, messageListener);
        ClientApp.getInstance().addMessageHandler(MessageType.LOGIN_FAILURE, messageListener);
    }

    private void assistantSpeak(String message, String color) {
        chatBubble.setText(message);
        chatBubble.setStyle(chatBubble.getStyle() + "-fx-text-fill: " + color + ";");
        chatBubble.setVisible(true);
        FadeTransition ft = new FadeTransition(Duration.millis(300), chatBubble);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    @FXML
    protected void onLoginButtonClicked(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        try {
            CredentialUtil.validateLogin(username, password);
        } catch (ValidationException e) {
            assistantSpeak(e.getMessage(), "#e67e22");
            return;
        }

        ClientApp.getInstance().getNetworkService().sendMessage(new LoginRequestMessage(username, password));
    }

    private void handleServerMessage(Message message) {
        if (!(message instanceof LoginResultMessage result)) {
            return;
        }

        if (message.getType() == MessageType.LOGIN_SUCCESS) {
            assistantSpeak(result.getMessage(), "#27ae60");

            ClientApp.getInstance().setCurrentUser(result.getUsername(), result.getRole());
            cleanupHandlers();
            SceneNavigator.switchSceneWithDelay("views/MainMenu.fxml", 1500);

        } else if (message.getType() == MessageType.LOGIN_FAILURE) {
            assistantSpeak(result.getMessage(), "#e74c3c");
        }
    }

    private void cleanupHandlers() {
        if (messageListener != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.LOGIN_SUCCESS, messageListener);
            ClientApp.getInstance().removeMessageHandler(MessageType.LOGIN_FAILURE, messageListener);
        }
    }

    @FXML
    public void switchToRegister(MouseEvent event) {
        cleanupHandlers();
        SceneNavigator.switchScene("views/RegisterMenu.fxml");
    }
}