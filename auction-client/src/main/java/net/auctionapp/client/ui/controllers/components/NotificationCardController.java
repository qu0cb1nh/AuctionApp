package net.auctionapp.client.ui.controllers.components;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import net.auctionapp.common.notifications.Notification;

public final class NotificationCardController {
    private static final PseudoClass CARD_HOVER_STATE = PseudoClass.getPseudoClass("card-hover");
    private static final PseudoClass OPENABLE_STATE = PseudoClass.getPseudoClass("openable");

    @FXML
    private HBox cardRoot;
    @FXML
    private Label typeLabel;
    @FXML
    private Label timeLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label bodyLabel;
    @FXML
    private Button clearButton;

    private Runnable clearAction;
    private Runnable openAuctionAction;
    private boolean clearHovered;

    public void bindNotification(
            Notification notification,
            String typeText,
            String timeText,
            String fallbackTitle,
            String fallbackBody
    ) {
        typeLabel.setText(typeText == null || typeText.isBlank() ? "Notification" : typeText);
        timeLabel.setText(timeText == null || timeText.isBlank() ? "Unknown time" : timeText);
        if (notification == null) {
            titleLabel.setText(fallbackTitle == null ? "Notification" : fallbackTitle);
            bodyLabel.setText(fallbackBody == null ? "" : fallbackBody);
            clearButton.setDisable(true);
            return;
        }

        titleLabel.setText(notification.getTitle() == null || notification.getTitle().isBlank()
                ? (fallbackTitle == null ? "Notification" : fallbackTitle)
                : notification.getTitle());
        bodyLabel.setText(notification.getBody() == null || notification.getBody().isBlank()
                ? (fallbackBody == null ? "" : fallbackBody)
                : notification.getBody());
        clearButton.setDisable(notification.getId() == null || notification.getId().isBlank());
    }

    public void setClearAction(Runnable clearAction) {
        this.clearAction = clearAction;
    }

    public void setOpenAuctionAction(Runnable openAuctionAction) {
        this.openAuctionAction = openAuctionAction;
        cardRoot.pseudoClassStateChanged(OPENABLE_STATE, openAuctionAction != null);
        updateCardStyle();
    }

    @FXML
    public void handleClear() {
        if (clearAction == null) {
            return;
        }
        clearAction.run();
    }

    @FXML
    public void handleOpenAuction(MouseEvent event) {
        if (openAuctionAction == null || isClearButtonTarget(event.getTarget())) {
            return;
        }
        openAuctionAction.run();
    }

    @FXML
    public void handleCardEntered() {
        updateCardStyle();
    }

    @FXML
    public void handleCardExited() {
        cardRoot.pseudoClassStateChanged(CARD_HOVER_STATE, false);
    }

    @FXML
    public void handleClearEntered() {
        clearHovered = true;
        updateCardStyle();
    }

    @FXML
    public void handleClearExited() {
        clearHovered = false;
        updateCardStyle();
    }

    private void updateCardStyle() {
        cardRoot.pseudoClassStateChanged(CARD_HOVER_STATE, !clearHovered && cardRoot.isHover());
    }

    private boolean isClearButtonTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node == clearButton) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }
}
