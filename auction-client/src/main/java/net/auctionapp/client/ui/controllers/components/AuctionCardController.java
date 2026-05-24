package net.auctionapp.client.ui.controllers.components;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.items.ItemType;

public final class AuctionCardController {
    @FXML
    private ImageView thumbnailImageView;
    @FXML
    private Label titleLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label detailOneLabel;
    @FXML
    private Label detailTwoLabel;
    @FXML
    private Label detailThreeLabel;
    @FXML
    private VBox metricOneBox;
    @FXML
    private Label metricOneCaptionLabel;
    @FXML
    private Label metricOneValueLabel;
    @FXML
    private VBox metricTwoBox;
    @FXML
    private Label metricTwoCaptionLabel;
    @FXML
    private Label metricTwoValueLabel;
    @FXML
    private VBox metricThreeBox;
    @FXML
    private Label metricThreeCaptionLabel;
    @FXML
    private Label metricThreeValueLabel;
    @FXML
    private Button primaryButton;
    @FXML
    private Button secondaryButton;
    @FXML
    private Button watchListButton;

    private Runnable primaryAction;
    private Runnable secondaryAction;
    private Runnable watchListAction;

    public void bindCard(CardData data) {
        if (data == null) {
            return;
        }
        setImage(data.imageUrl(), data.itemType());
        titleLabel.setText(textOrFallback(data.title(), "Untitled auction"));
        statusLabel.setText(textOrFallback(data.statusText(), ""));
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: "
                + textOrFallback(data.statusColor(), "#3f5569") + ";");
        setOptionalLabel(detailOneLabel, data.detailOne());
        setOptionalLabel(detailTwoLabel, data.detailTwo());
        setOptionalLabel(detailThreeLabel, data.detailThree());
        setMetric(metricOneBox, metricOneCaptionLabel, metricOneValueLabel, data.metricOneCaption(), data.metricOneValue(), data.metricOneColor());
        setMetric(metricTwoBox, metricTwoCaptionLabel, metricTwoValueLabel, data.metricTwoCaption(), data.metricTwoValue(), data.metricTwoColor());
        setMetric(metricThreeBox, metricThreeCaptionLabel, metricThreeValueLabel, data.metricThreeCaption(), data.metricThreeValue(), data.metricThreeColor());
        configureButton(primaryButton, data.primaryButtonText(), data.primaryAction());
        configureButton(secondaryButton, data.secondaryButtonText(), data.secondaryAction());
        configureButton(watchListButton, data.watchListButtonText(), data.watchListAction());
        if (data.watchListButtonText() != null && data.watchListButtonText().equals("Watching")) {
            watchListButton.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #c62828; -fx-background-radius: 6; -fx-font-weight: bold; -fx-cursor: hand;");
        } else {
            watchListButton.setStyle("-fx-background-color: #e7f8fb; -fx-text-fill: #217b93; -fx-background-radius: 6; -fx-font-weight: bold; -fx-cursor: hand;");
        }
    }

    @FXML
    public void handlePrimaryAction(ActionEvent event) {
        if (primaryAction != null) {
            primaryAction.run();
        }
    }

    @FXML
    public void handleSecondaryAction(ActionEvent event) {
        if (secondaryAction != null) {
            secondaryAction.run();
        }
    }

    @FXML
    public void handleWatchListAction(ActionEvent event) {
        if (watchListAction != null) {
            watchListAction.run();
        }
    }

    private void setImage(String imageUrl, ItemType itemType) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            thumbnailImageView.setImage(new Image(imageUrl, true));
            return;
        }
        Image fallback = new Image(ResourcesUtil.itemPlaceholder(itemType).toExternalForm(), true);
        thumbnailImageView.setImage(fallback);
    }

    private void setOptionalLabel(Label label, String text) {
        boolean visible = text != null && !text.isBlank();
        label.setText(visible ? text : "");
        label.setManaged(visible);
        label.setVisible(visible);
    }

    private void setMetric(VBox metricBox, Label captionLabel, Label valueLabel, String caption, String value, String color) {
        boolean visible = (caption != null && !caption.isBlank()) || (value != null && !value.isBlank());
        metricBox.setManaged(visible);
        metricBox.setVisible(visible);
        if (!visible) {
            return;
        }
        captionLabel.setText(textOrFallback(caption, ""));
        valueLabel.setText(textOrFallback(value, "N/A"));
        valueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: "
                + textOrFallback(color, "#1f2933") + ";");
    }

    private void configureButton(Button button, String text, Runnable action) {
        boolean visible = text != null && !text.isBlank() && action != null;
        button.setText(visible ? text : "");
        button.setManaged(visible);
        button.setVisible(visible);
        if (button == primaryButton) {
            primaryAction = action;
            return;
        }
        if (button == secondaryButton) {
            secondaryAction = action;
            return;
        }
        watchListAction = action;
    }

    private String textOrFallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    public record CardData(
            String imageUrl,
            ItemType itemType,
            String title,
            String statusText,
            String statusColor,
            String detailOne,
            String detailTwo,
            String detailThree,
            String metricOneCaption,
            String metricOneValue,
            String metricOneColor,
            String metricTwoCaption,
            String metricTwoValue,
            String metricTwoColor,
            String metricThreeCaption,
            String metricThreeValue,
            String metricThreeColor,
            String primaryButtonText,
            Runnable primaryAction,
            String secondaryButtonText,
            Runnable secondaryAction,
            String watchListButtonText,
            Runnable watchListAction
    ) {}
}
