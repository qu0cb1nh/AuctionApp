package net.auctionapp.client.controllers;

import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class AssistantPanelController {
    @FXML
    private Label chatBubble;

    public void speak(String message, String color) {
        chatBubble.setText(message);
        chatBubble.setStyle(chatBubble.getStyle() + "-fx-text-fill: " + color + ";");
        chatBubble.setVisible(true);

        FadeTransition transition = new FadeTransition(Duration.millis(300), chatBubble);
        transition.setFromValue(0);
        transition.setToValue(1);
        transition.play();
    }
}
