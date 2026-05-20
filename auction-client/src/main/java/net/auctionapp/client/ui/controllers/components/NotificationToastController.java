package net.auctionapp.client.ui.controllers.components;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
public final class NotificationToastController {
    @FXML
    private Label titleLabel;

    @FXML
    private Label bodyLabel;

    public void setContent(String title, String body) {
        titleLabel.setText(title == null || title.isBlank() ? "Notification" : title);
        bodyLabel.setText(body == null ? "" : body);
    }
}
