package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.services.WatchListService;
import net.auctionapp.client.ui.controllers.components.AuctionCardController;
import net.auctionapp.client.ui.controllers.components.HeaderController;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionSummary;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.WatchListChangedMessage;
import net.auctionapp.common.messages.types.WatchListResponseMessage;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class WatchListMenuController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox auctionFlowPane;
    @FXML
    private Label listStatusLabel;

    private final List<AuctionSummary> watchedAuctions = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("My Watchlist");
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
        requestWatchList();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        requestWatchList();
    }

    private void requestWatchList() {
        showListStatus("Loading watchlist...", "-fx-text-fill: #666666;");
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
    }

    private void handleWatchListResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), "-fx-text-fill: #d9534f;");
            return;
        }
        if (!(message instanceof WatchListResponseMessage response)) {
            showListStatus("Unexpected response from server.", "-fx-text-fill: #d9534f;");
            return;
        }
        watchedAuctions.clear();
        watchedAuctions.addAll(response.getAuctions());
        renderAuctionCards();
    }

    private void handleWatchListChanged(WatchListChangedMessage changed) {
        if (changed == null || changed.getAuctionId() == null) {
            return;
        }
        if (changed.isWatched()) {
            requestWatchList();
            return;
        }
        watchedAuctions.removeIf(auction -> changed.getAuctionId().equals(auction.getAuctionId()));
        renderAuctionCards();
    }

    private void renderAuctionCards() {
        auctionFlowPane.getChildren().clear();
        if (watchedAuctions.isEmpty()) {
            showListStatus("Your watchlist is empty.", "-fx-text-fill: #666666;");
            return;
        }
        hideListStatus();
        for (AuctionSummary auction : watchedAuctions) {
            auctionFlowPane.getChildren().add(loadAuctionCard(auction));
        }
    }

    private HBox loadAuctionCard(AuctionSummary auction) {
        boolean canManageAuction = ClientSession.getInstance().canManageAuction(auction.getSellerId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                auction.getImageUrl(),
                auction.getItemType(),
                auction.getTitle(),
                "Owner: " + formatOwner(auction.getSellerUsername()),
                "#4a5f73",
                "Minimum Next Bid: " + formatPrice(auction.getMinimumNextBid()),
                "Start: " + formatDateTime(auction.getStartTime()),
                "End: " + formatDateTime(auction.getEndTime()),
                "Current Bid",
                formatPrice(auction.getCurrentPrice()),
                "#0057ff",
                "Ends At",
                formatDateTime(auction.getEndTime()),
                "#e03621",
                "Top Bidder",
                formatTopBidder(auction.getLeadingBidderUsername()),
                "#1f2933",
                "View auction",
                () -> SceneManager.switchToAuctionDetails(auction.getAuctionId()),
                canManageAuction ? "Manage auction" : null,
                canManageAuction ? () -> SceneManager.switchToManageAuction(auction.getAuctionId()) : null,
                "Watching",
                () -> handleRemoveFromWatchList(auction.getAuctionId())
        );
        try {
            FXMLLoader loader = ResourcesUtil.fxmlLoader("components/AuctionCard.fxml");
            HBox card = loader.load();
            AuctionCardController controller = loader.getController();
            controller.bindCard(cardData);
            return card;
        } catch (IOException | RuntimeException e) {
            Label fallback = new Label("Failed to load watchlist auction.");
            fallback.setStyle("-fx-text-fill: #d9534f;");
            return new HBox(fallback);
        }
    }

    private void handleRemoveFromWatchList(String auctionId) {
        WatchListService.getInstance().updateWatched(auctionId, false, this::handleUpdateResponse);
    }

    private void handleUpdateResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), "-fx-text-fill: #d9534f;");
            return;
        }
        if (message instanceof WatchListChangedMessage changed) {
            handleWatchListChanged(changed);
            return;
        }
        showListStatus("Unexpected response from server.", "-fx-text-fill: #d9534f;");
    }

    private String formatPrice(BigDecimal value) {
        return value == null ? "N/A" : "$" + value.stripTrailingZeros().toPlainString();
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "N/A" : CARD_TIME_FORMATTER.format(time);
    }

    private String formatTopBidder(String bidderId) {
        return bidderId == null || bidderId.isBlank() ? "No bids yet" : bidderId;
    }

    private String formatOwner(String sellerUsername) {
        return sellerUsername == null || sellerUsername.isBlank() ? "Unknown" : sellerUsername;
    }

    private void hideListStatus() {
        listStatusLabel.setText("");
        listStatusLabel.setManaged(false);
        listStatusLabel.setVisible(false);
    }

    private void showListStatus(String text, String style) {
        listStatusLabel.setManaged(true);
        listStatusLabel.setVisible(true);
        listStatusLabel.setStyle(style);
        listStatusLabel.setText(text);
    }
}
