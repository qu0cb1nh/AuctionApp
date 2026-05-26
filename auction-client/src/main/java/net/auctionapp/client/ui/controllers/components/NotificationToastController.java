package net.auctionapp.client.ui.controllers.components;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

public final class NotificationToastController {
    @FXML
    private HBox toastRoot;

    @FXML
    private Label titleLabel;

    @FXML
    private Label bodyLabel;

    public void setContent(String title, String body) {
        titleLabel.setText(title == null || title.isBlank() ? "Notification" : title);
        bodyLabel.setText(body == null ? "" : body);
    }

    @FXML
    private void handleClose(ActionEvent event) {
        if (toastRoot.getParent() instanceof Pane parent) {
            parent.getChildren().remove(toastRoot);
        }
        event.consume();
    }

    @FXML
    private void consumeCloseClick(MouseEvent event) {
        event.consume();
    }
}
