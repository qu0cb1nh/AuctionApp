package net.auctionapp.client.ui.controllers;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.services.WatchListService;
import net.auctionapp.client.ui.controllers.components.AuctionCardController;
import net.auctionapp.client.ui.controllers.components.AuctionToolBarController;
import net.auctionapp.client.utils.AuctionCardUtil;
import net.auctionapp.client.ui.controllers.components.HeaderController;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.AuctionDisplayUtil;
import net.auctionapp.client.utils.FxViewUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;
import net.auctionapp.common.dto.AuctionSummaryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WatchListMenuController {
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final String STATUS_ALL = "All";
    private static final String STATUS_RUNNING = "Running";
    private static final String STATUS_PAID = "Paid";
    private static final String STATUS_CANCELED = "Canceled";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox auctionFlowPane;
    @FXML
    private Label listStatusLabel;
    @FXML
    private AuctionToolBarController auctionToolBarController;

    private final List<AuctionSummaryDto> watchedAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("My watchlist");
        auctionToolBarController.setup(
                "Search your watchlist...",
                List.of(STATUS_ALL, STATUS_RUNNING, STATUS_PAID, STATUS_CANCELED),
                STATUS_ALL,
                this::applyFilters,
                this::requestWatchList
        );
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
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
        applyFilters();
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
        applyFilters();
    }

    private void applyFilters() {
        String search = auctionToolBarController.getSearchText();
        String selectedStatus = auctionToolBarController.getSelectedFilter();
        List<AuctionSummaryDto> filtered = watchedAuctions.stream()
                .filter(auction -> search.isBlank() || auction.getTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(auction -> selectedStatus == null
                        || STATUS_ALL.equalsIgnoreCase(selectedStatus)
                        || AuctionDisplayUtil.displayStatus(auction).equalsIgnoreCase(selectedStatus))
                .toList();
        renderAuctionCards(filtered);
    }

    private void renderAuctionCards(List<AuctionSummaryDto> auctions) {
        auctionFlowPane.getChildren().clear();
        if (auctions.isEmpty()) {
            showListStatus(
                    watchedAuctions.isEmpty() ? "Your watchlist is empty." : "No watched auctions match your filters.",
                    false
            );
            return;
        }
        showListStatus(null, false);
        for (AuctionSummaryDto auction : auctions) {
            auctionFlowPane.getChildren().add(loadAuctionCard(auction));
        }
    }

    private HBox loadAuctionCard(AuctionSummaryDto auction) {
        boolean active = "RUNNING".equals(AuctionDisplayUtil.displayStatus(auction));
        boolean canManageAuction = ClientSession.getInstance().canManageAuction(auction.getSellerId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                auction.getImageUrl(),
                auction.getItemType(),
                auction.getTitle(),
                "Owner: " + AuctionDisplayUtil.formatOwner(auction.getSellerUsername()),
                AuctionCardController.TextTone.MUTED,
                "Minimum next bid: " + AuctionDisplayUtil.formatPrice(auction.getMinimumNextBid()),
                "Start: " + AuctionDisplayUtil.formatDateTime(auction.getStartTime()),
                null,
                "Current bid",
                AuctionDisplayUtil.formatPrice(auction.getCurrentPrice()),
                AuctionCardController.TextTone.PRIMARY,
                "Top bidder",
                AuctionDisplayUtil.formatBidder(auction.getLeadingBidderUsername()),
                AuctionCardController.TextTone.DEFAULT,
                active ? "Ends in" : "Ended at",
                active ? "00:00:00" : AuctionDisplayUtil.formatDateTime(auction.getEndTime()),
                active ? AuctionCardController.TextTone.DANGER : AuctionCardController.TextTone.DEFAULT,
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
        return AuctionCardUtil.createWithMetricCountdown(
                cardData,
                active ? auction.getEndTime() : null,
                "Failed to load watchlist auction."
        );
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
