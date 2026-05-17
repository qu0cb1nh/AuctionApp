package net.auctionapp.client.ui.controllers.components;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import net.auctionapp.common.notifications.Notification;

public final class NotificationCardController {
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

    @FXML
    public void handleClear(ActionEvent event) {
        if (clearAction == null) {
            return;
        }
        clearAction.run();
    }
}
