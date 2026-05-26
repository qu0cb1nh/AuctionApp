package net.auctionapp.client.ui.controllers.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.auctionapp.client.utils.DurationFormatUtil;
import net.auctionapp.client.utils.FxViewUtil;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.items.ItemType;

import java.time.LocalDateTime;

public final class AuctionCardController {
    private static final PseudoClass WATCHING_STATE = PseudoClass.getPseudoClass("watching");
    private static final PseudoClass MUTED_STATE = PseudoClass.getPseudoClass("muted");
    private static final PseudoClass PRIMARY_STATE = PseudoClass.getPseudoClass("primary");
    private static final PseudoClass DANGER_STATE = PseudoClass.getPseudoClass("danger");
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
    private Timeline countdownTimeline;

    @FXML
    private void initialize() {
        titleLabel.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                stopCountdownTimer();
            }
        });
    }

    public void bindCard(CardData data) {
        if (data == null) {
            return;
        }
        setImage(data.imageUrl(), data.itemType());
        titleLabel.setText(textOrFallback(data.title(), "Untitled auction"));
        statusLabel.setText(textOrFallback(data.statusText(), ""));
        setTone(statusLabel, data.statusTone());
        setOptionalLabel(detailOneLabel, data.detailOne());
        setOptionalLabel(detailTwoLabel, data.detailTwo());
        setOptionalLabel(detailThreeLabel, data.detailThree());
        setMetric(metricOneBox, metricOneCaptionLabel, metricOneValueLabel, data.metricOneCaption(), data.metricOneValue(), data.metricOneTone());
        setMetric(metricTwoBox, metricTwoCaptionLabel, metricTwoValueLabel, data.metricTwoCaption(), data.metricTwoValue(), data.metricTwoTone());
        setMetric(metricThreeBox, metricThreeCaptionLabel, metricThreeValueLabel, data.metricThreeCaption(), data.metricThreeValue(), data.metricThreeTone());
        configureButton(primaryButton, data.primaryButtonText(), data.primaryAction());
        configureButton(secondaryButton, data.secondaryButtonText(), data.secondaryAction());
        configureButton(watchListButton, data.watchListButtonText(), data.watchListAction());
        watchListButton.pseudoClassStateChanged(
                WATCHING_STATE,
                "Watching".equals(data.watchListButtonText())
        );
    }

    public void startDetailTwoCountdown(LocalDateTime endTime) {
        startCountdown(detailTwoLabel, "Ends in: ", endTime);
    }

    public void startMetricTwoCountdown(LocalDateTime endTime) {
        startCountdown(metricTwoValueLabel, "", endTime);
    }

    @FXML
    public void handlePrimaryAction() {
        if (primaryAction != null) {
            primaryAction.run();
        }
    }

    @FXML
    public void handleSecondaryAction() {
        if (secondaryAction != null) {
            secondaryAction.run();
        }
    }

    @FXML
    public void handleWatchListAction() {
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
        FxViewUtil.setVisible(label, visible);
    }

    private void setMetric(VBox metricBox, Label captionLabel, Label valueLabel, String caption, String value, TextTone tone) {
        boolean visible = (caption != null && !caption.isBlank()) || (value != null && !value.isBlank());
        FxViewUtil.setVisible(metricBox, visible);
        if (!visible) {
            return;
        }
        captionLabel.setText(textOrFallback(caption, ""));
        valueLabel.setText(textOrFallback(value, "N/A"));
        setTone(valueLabel, tone);
    }

    private void setTone(Label label, TextTone tone) {
        TextTone resolvedTone = tone == null ? TextTone.DEFAULT : tone;
        label.pseudoClassStateChanged(MUTED_STATE, resolvedTone == TextTone.MUTED);
        label.pseudoClassStateChanged(PRIMARY_STATE, resolvedTone == TextTone.PRIMARY);
        label.pseudoClassStateChanged(DANGER_STATE, resolvedTone == TextTone.DANGER);
    }

    private void configureButton(Button button, String text, Runnable action) {
        boolean visible = text != null && !text.isBlank() && action != null;
        button.setText(visible ? text : "");
        FxViewUtil.setVisible(button, visible);
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

    private void startCountdown(Label label, String prefix, LocalDateTime endTime) {
        if (endTime == null) {
            return;
        }
        stopCountdownTimer();
        updateCountdownLabel(label, prefix, endTime);
        if (!LocalDateTime.now().isBefore(endTime)) {
            return;
        }
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            updateCountdownLabel(label, prefix, endTime);
            if (!LocalDateTime.now().isBefore(endTime)) {
                stopCountdownTimer();
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateCountdownLabel(Label label, String prefix, LocalDateTime endTime) {
        java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), endTime);
        label.setText(prefix + DurationFormatUtil.formatRemainingDuration(remaining));
    }

    private void stopCountdownTimer() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
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
            TextTone statusTone,
            String detailOne,
            String detailTwo,
            String detailThree,
            String metricOneCaption,
            String metricOneValue,
            TextTone metricOneTone,
            String metricTwoCaption,
            String metricTwoValue,
            TextTone metricTwoTone,
            String metricThreeCaption,
            String metricThreeValue,
            TextTone metricThreeTone,
            String primaryButtonText,
            Runnable primaryAction,
            String secondaryButtonText,
            Runnable secondaryAction,
            String watchListButtonText,
            Runnable watchListAction
    ) {}

    public enum TextTone {
        DEFAULT,
        MUTED,
        PRIMARY,
        DANGER
    }
}
