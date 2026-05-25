package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.css.PseudoClass;
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
import net.auctionapp.common.dto.AuctionSummary;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;

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
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");

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
        showListStatus("Loading watchlist...", false);
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
    }

    private void handleWatchListResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof WatchListResponseMessage response)) {
            showListStatus("Unexpected response from server.", true);
            return;
        }
        watchedAuctions.clear();
        watchedAuctions.addAll(response.getAuctions());
        renderAuctionCards();
    }

    private void handleWatchListChanged(WatchListChangedResponseMessage changed) {
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
            showListStatus("Your watchlist is empty.", false);
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
                AuctionCardController.TextTone.MUTED,
                "Minimum Next Bid: " + formatPrice(auction.getMinimumNextBid()),
                "Start: " + formatDateTime(auction.getStartTime()),
                "End: " + formatDateTime(auction.getEndTime()),
                "Current Bid",
                formatPrice(auction.getCurrentPrice()),
                AuctionCardController.TextTone.PRIMARY,
                "Ends At",
                formatDateTime(auction.getEndTime()),
                AuctionCardController.TextTone.DANGER,
                "Top Bidder",
                formatTopBidder(auction.getLeadingBidderUsername()),
                AuctionCardController.TextTone.DEFAULT,
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
            fallback.getStyleClass().add("load-error");
            return new HBox(fallback);
        }
    }

    private void handleRemoveFromWatchList(String auctionId) {
        WatchListService.getInstance().updateWatched(auctionId, false, this::handleUpdateResponse);
    }

    private void handleUpdateResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (message instanceof WatchListChangedResponseMessage changed) {
            handleWatchListChanged(changed);
            return;
        }
        showListStatus("Unexpected response from server.", true);
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

    private void showListStatus(String text, boolean error) {
        listStatusLabel.setManaged(true);
        listStatusLabel.setVisible(true);
        listStatusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        listStatusLabel.setText(text);
    }
}
