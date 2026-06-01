package net.auctionapp.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import net.auctionapp.client.ui.controllers.components.AuctionCardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

public final class AuctionCardUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionCardUtil.class);

    private AuctionCardUtil() {
    }

    public static HBox create(AuctionCardController.CardData data, String failureText) {
        return create(data, null, failureText);
    }

    public static HBox createWithMetricCountdown(
            AuctionCardController.CardData data,
            LocalDateTime endTime,
            String failureText
    ) {
        return create(data, endTime, failureText);
    }

    private static HBox create(
            AuctionCardController.CardData data,
            LocalDateTime endTime,
            String failureText
    ) {
        try {
            FXMLLoader loader = ResourcesUtil.fxmlLoader("components/AuctionCard.fxml");
            HBox card = loader.load();
            AuctionCardController controller = loader.getController();
            controller.bindCard(data);
            controller.startMetricThreeCountdown(endTime);
            return card;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to render auction card.", e);
            Label fallback = new Label(failureText);
            fallback.getStyleClass().add("load-error");
            return new HBox(fallback);
        }
    }
}
