package net.auctionapp.client.ui.controllers;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.services.WatchListService;
import net.auctionapp.client.ui.controllers.components.AuctionCardController;
import net.auctionapp.client.utils.AuctionCardUtil;
import net.auctionapp.client.ui.controllers.components.HeaderController;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.AuctionDisplayUtil;
import net.auctionapp.client.utils.FxViewUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.dto.AuctionSummary;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;

import java.util.ArrayList;
import java.util.List;

public class WatchListMenuController {
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox auctionFlowPane;
    @FXML
    private Label listStatusLabel;

    private final List<AuctionSummary> watchedAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("My Watchlist");
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
        requestWatchList();
    }

    @FXML
    public void handleRefresh() {
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
        showListStatus(null, false);
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
                "Owner: " + AuctionDisplayUtil.formatOwner(auction.getSellerUsername()),
                AuctionCardController.TextTone.MUTED,
                "Minimum Next Bid: " + AuctionDisplayUtil.formatPrice(auction.getMinimumNextBid()),
                "Start: " + AuctionDisplayUtil.formatDateTime(auction.getStartTime()),
                "End: " + AuctionDisplayUtil.formatDateTime(auction.getEndTime()),
                "Current Bid",
                AuctionDisplayUtil.formatPrice(auction.getCurrentPrice()),
                AuctionCardController.TextTone.PRIMARY,
                "Ends At",
                AuctionDisplayUtil.formatDateTime(auction.getEndTime()),
                AuctionCardController.TextTone.DANGER,
                "Top Bidder",
                AuctionDisplayUtil.formatBidder(auction.getLeadingBidderUsername()),
                AuctionCardController.TextTone.DEFAULT,
                "View auction",
                () -> SceneManager.switchToAuctionDetails(auction.getAuctionId()),
                canManageAuction ? "Manage auction" : null,
                canManageAuction ? () -> SceneManager.switchToManageAuction(auction.getAuctionId()) : null,
                "Watching",
                () -> WatchListService.getInstance().updateWatched(
                        auction.getAuctionId(),
                        false,
                        this::handleUpdateResponse
                )
        );
        return AuctionCardUtil.create(cardData, "Failed to load watchlist auction.");
    }

    private void handleUpdateResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (message instanceof WatchListChangedResponseMessage changed) {
            handleWatchListChanged(changed);
            NotificationToastManager.showSuccess("Auction removed from your watchlist.");
            return;
        }
        showListStatus("Unexpected response from server.", true);
    }

    private void showListStatus(String text, boolean error) {
        boolean visible = text != null && !text.isBlank();
        FxViewUtil.setVisible(listStatusLabel, visible);
        listStatusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        listStatusLabel.setText(visible ? text : "");
    }
}
