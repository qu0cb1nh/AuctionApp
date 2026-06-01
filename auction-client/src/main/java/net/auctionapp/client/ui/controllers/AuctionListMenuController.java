package net.auctionapp.client.ui.controllers;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.services.AuctionService;
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
import net.auctionapp.common.messages.auction.AuctionListResponseMessage;
import net.auctionapp.common.dto.AuctionSummaryDto;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AuctionListMenuController {
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

    private final List<AuctionSummaryDto> allAuctions = new ArrayList<>();
    private final Set<String> watchedAuctionIds = new HashSet<>();
    private boolean authenticatedUser;
    private boolean watchListLoaded;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Explore auctions");
        authenticatedUser = ClientSession.getInstance().isAuthenticated();
        auctionToolBarController.setup(
                "Search for products...",
                List.of(STATUS_ALL, STATUS_RUNNING, STATUS_PAID, STATUS_CANCELED),
                STATUS_RUNNING,
                this::applyFilters,
                this::requestAuctionList
        );
        SceneManager.registerSceneMessageListener(MessageType.AUCTION_UPDATED, update -> requestAuctionList());

        if (authenticatedUser) {
            SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
            requestWatchList();
        }
        requestAuctionList();
    }

    private void requestAuctionList() {
        showListStatus("Loading auctions...", false);
        AuctionService.getInstance().requestAuctionList(this::handleAuctionListResult);
    }

    private void handleAuctionListResult(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            showListStatus("Unexpected response from server.", true);
            return;
        }

        allAuctions.clear();
        allAuctions.addAll(response.getAuctions());
        applyFilters();
    }

    private void requestWatchList() {
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
    }

    private void handleWatchListResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof WatchListResponseMessage response)) {
            showListStatus("Unexpected watch list response from server.", true);
            return;
        }
        watchedAuctionIds.clear();
        response.getAuctions().stream()
                .filter(auction -> auction != null && auction.getAuctionId() != null)
                .map(AuctionSummaryDto::getAuctionId)
                .forEach(watchedAuctionIds::add);
        watchListLoaded = true;
        applyFilters();
    }

    private void applyFilters() {
        String search = auctionToolBarController.getSearchText();
        String selectedStatus = auctionToolBarController.getSelectedFilter();

        List<AuctionSummaryDto> filtered = allAuctions.stream()
                .filter(auction -> search.isBlank() || auction.getTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(auction -> selectedStatus == null
                        || STATUS_ALL.equalsIgnoreCase(selectedStatus)
                        || AuctionDisplayUtil.displayStatus(auction).equalsIgnoreCase(selectedStatus))
                .toList();

        renderAuctionCards(filtered);
        if (allAuctions.isEmpty()) {
            showListStatus("No auctions available.", false);
            return;
        }
        if (filtered.isEmpty()) {
            showListStatus("No auctions match your search/filter.", false);
            return;
        }
        showListStatus(null, false);
    }

    private void renderAuctionCards(List<AuctionSummaryDto> auctions) {
        auctionFlowPane.getChildren().clear();
        if (auctions.isEmpty()) {
            Label emptyLabel = new Label("No auctions found.");
            emptyLabel.getStyleClass().add("empty-state");
            auctionFlowPane.getChildren().add(emptyLabel);
            return;
        }

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
                authenticatedUser && watchListLoaded
                        ? AuctionDisplayUtil.watchListButtonText(watchedAuctionIds.contains(auction.getAuctionId()))
                        : null,
                authenticatedUser && watchListLoaded
                        ? () -> handleToggleWatchList(auction.getAuctionId())
                        : null
        );
        return AuctionCardUtil.createWithMetricCountdown(
                cardData,
                active ? auction.getEndTime() : null,
                "Failed to load auction card."
        );
    }

    private void handleToggleWatchList(String auctionId) {
        WatchListService.getInstance().updateWatched(
                auctionId,
                !watchedAuctionIds.contains(auctionId),
                this::handleWatchListUpdateResponse
        );
    }

    private void handleWatchListUpdateResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showListStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof WatchListChangedResponseMessage changed)) {
            showListStatus("Unexpected watch list response from server.", true);
            return;
        }
        handleWatchListChanged(changed);
        NotificationToastManager.showSuccess(AuctionDisplayUtil.watchListActionMessage(changed.isWatched()));
    }

    private void handleWatchListChanged(WatchListChangedResponseMessage changed) {
        if (changed == null || changed.getAuctionId() == null) {
            return;
        }
        if (changed.isWatched()) {
            watchedAuctionIds.add(changed.getAuctionId());
        } else {
            watchedAuctionIds.remove(changed.getAuctionId());
        }
        applyFilters();
    }

    private void showListStatus(String text, boolean error) {
        boolean visible = text != null && !text.isBlank();
        FxViewUtil.setVisible(listStatusLabel, visible);
        listStatusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        listStatusLabel.setText(visible ? text : "");
    }

}
