package net.auctionapp.client.ui.controllers.components;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import net.auctionapp.common.notifications.Notification;

public final class NotificationCardController {
    private static final String CARD_STYLE =
            "-fx-background-color: white; -fx-border-color: #d6e4f0; -fx-border-width: 1; "
                    + "-fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14;";
    private static final String CARD_HOVER_STYLE =
            "-fx-background-color: #f0f8ff; -fx-border-color: #3bb3d1; -fx-border-width: 1; "
                    + "-fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14;";
    private static final String CLEAR_STYLE =
            "-fx-background-color: #f3f4f6; -fx-border-color: #d1d5db; -fx-border-radius: 6; "
                    + "-fx-background-radius: 6; -fx-padding: 5 12; -fx-text-fill: #374151; "
                    + "-fx-font-weight: bold; -fx-cursor: hand;";
    private static final String CLEAR_HOVER_STYLE =
            "-fx-background-color: #e0f2fe; -fx-border-color: #3bb3d1; -fx-border-radius: 6; "
                    + "-fx-background-radius: 6; -fx-padding: 5 12; -fx-text-fill: #153e5c; "
                    + "-fx-font-weight: bold; -fx-cursor: hand;";

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
        updateCardStyle();
    }

    @FXML
    public void handleClear(ActionEvent event) {
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
    public void handleCardEntered(MouseEvent event) {
        updateCardStyle();
    }

    @FXML
    public void handleCardExited(MouseEvent event) {
        cardRoot.setStyle(CARD_STYLE);
    }

    @FXML
    public void handleClearEntered(MouseEvent event) {
        clearHovered = true;
        cardRoot.setStyle(CARD_STYLE);
        clearButton.setStyle(CLEAR_HOVER_STYLE);
    }

    @FXML
    public void handleClearExited(MouseEvent event) {
        clearHovered = false;
        clearButton.setStyle(CLEAR_STYLE);
        updateCardStyle();
    }

    private void updateCardStyle() {
        if (clearHovered || !cardRoot.isHover()) {
            cardRoot.setStyle(CARD_STYLE);
            return;
        }
        String cursorStyle = openAuctionAction == null ? "" : " -fx-cursor: hand;";
        cardRoot.setStyle(CARD_HOVER_STYLE + cursorStyle);
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
