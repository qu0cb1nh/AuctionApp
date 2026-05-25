package net.auctionapp.client.ui.controllers.components;

import javafx.animation.FadeTransition;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class AssistantPanelController {
    private static final PseudoClass WARNING_STATE = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SUCCESS_STATE = PseudoClass.getPseudoClass("success");

    @FXML
    private Label chatBubble;

    public void speak(String message, Tone tone) {
        chatBubble.setText(message);
        chatBubble.pseudoClassStateChanged(WARNING_STATE, tone == Tone.WARNING);
        chatBubble.pseudoClassStateChanged(ERROR_STATE, tone == Tone.ERROR);
        chatBubble.pseudoClassStateChanged(SUCCESS_STATE, tone == Tone.SUCCESS);
        chatBubble.setVisible(true);

        FadeTransition transition = new FadeTransition(Duration.millis(300), chatBubble);
        transition.setFromValue(0);
        transition.setToValue(1);
        transition.play();
    }

    public enum Tone {
        WARNING,
        ERROR,
        SUCCESS
    }
}
