package net.auctionapp.client.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import net.auctionapp.common.notifications.NotificationView;

public final class NotificationToastController {
    @FXML
    private Label titleLabel;

    @FXML
    private Label bodyLabel;

    public void setNotification(NotificationView notification) {
        if (notification == null) {
            titleLabel.setText("Notification");
            bodyLabel.setText("");
            return;
        }
        titleLabel.setText(notification.getTitle() == null ? "Notification" : notification.getTitle());
        bodyLabel.setText(notification.getBody() == null ? "" : notification.getBody());
    }
}
