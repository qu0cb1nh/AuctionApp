package net.auctionapp.client.ui.controllers;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.services.WatchListService;
import net.auctionapp.client.ui.controllers.components.AuctionCardController;
import net.auctionapp.client.utils.AuctionCardUtil;
import net.auctionapp.client.ui.controllers.components.HeaderController;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.AuctionDisplayUtil;
import net.auctionapp.client.utils.FxViewUtil;
import net.auctionapp.common.dto.AuctionSummary;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auction.AuctionListResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AuctionListMenuController {
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final String STATUS_ALL = "ALL";
    private static final String SORT_ENDING_SOON = "Ending soon";
    private static final String SORT_HIGHEST_BID = "Highest bid";
    private static final String SORT_NEWEST_START = "Newest start";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox auctionFlowPane;
    @FXML
    private Label listStatusLabel;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private ComboBox<String> sortComboBox;

    private final List<AuctionSummary> allAuctions = new ArrayList<>();
    private final Set<String> watchedAuctionIds = new HashSet<>();
    private boolean authenticatedUser;
    private boolean watchListLoaded;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Explore Auctions");
        authenticatedUser = ClientSession.getInstance().isAuthenticated();
        statusFilterComboBox.getItems().setAll(
                STATUS_ALL,
                "RUNNING",
                "PAID",
                "CANCELED"
        );
        statusFilterComboBox.getSelectionModel().select("RUNNING");
        sortComboBox.getItems().setAll(SORT_ENDING_SOON, SORT_HIGHEST_BID, SORT_NEWEST_START);
        sortComboBox.getSelectionModel().select(SORT_ENDING_SOON);
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
                .map(AuctionSummary::getAuctionId)
                .forEach(watchedAuctionIds::add);
        watchListLoaded = true;
        applyFilters();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    @FXML
    public void handleRefresh() {
        requestAuctionList();
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();
        String selectedSort = sortComboBox.getSelectionModel().getSelectedItem();

        List<AuctionSummary> filtered = allAuctions.stream()
                .filter(auction -> search.isBlank() || auction.getTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(auction -> selectedStatus == null
                        || STATUS_ALL.equalsIgnoreCase(selectedStatus)
                        || AuctionDisplayUtil.displayStatus(auction).equalsIgnoreCase(selectedStatus))
                .toList();

        List<AuctionSummary> sorted = sortAuctions(filtered, selectedSort);
        renderAuctionCards(sorted);
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

    private void renderAuctionCards(List<AuctionSummary> auctions) {
        auctionFlowPane.getChildren().clear();
        if (auctions.isEmpty()) {
            Label emptyLabel = new Label("No auctions found.");
            emptyLabel.getStyleClass().add("empty-state");
            auctionFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (AuctionSummary auction : auctions) {
            auctionFlowPane.getChildren().add(loadAuctionCard(auction));
        }
    }

    private HBox loadAuctionCard(AuctionSummary auction) {
        boolean active = "RUNNING".equals(AuctionDisplayUtil.displayStatus(auction));
        boolean canManageAuction = ClientSession.getInstance().canManageAuction(auction.getSellerId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                auction.getImageUrl(),
                auction.getItemType(),
                auction.getTitle(),
                "Owner: " + AuctionDisplayUtil.formatOwner(auction.getSellerUsername()),
                AuctionCardController.TextTone.MUTED,
                "Minimum Next Bid: " + AuctionDisplayUtil.formatPrice(auction.getMinimumNextBid()),
                "Start: " + AuctionDisplayUtil.formatDateTime(auction.getStartTime()),
                null,
                "Current Bid",
                AuctionDisplayUtil.formatPrice(auction.getCurrentPrice()),
                AuctionCardController.TextTone.PRIMARY,
                active ? "Ends In" : "Ended At",
                active ? "00:00:00" : AuctionDisplayUtil.formatDateTime(auction.getEndTime()),
                AuctionCardController.TextTone.DANGER,
                "Top Bidder",
                AuctionDisplayUtil.formatBidder(auction.getLeadingBidderUsername()),
                AuctionCardController.TextTone.DEFAULT,
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

    private List<AuctionSummary> sortAuctions(List<AuctionSummary> auctions, String selectedSort) {
        String sortKey = selectedSort == null ? SORT_ENDING_SOON : selectedSort;
        Comparator<AuctionSummary> comparator = switch (sortKey) {
            case SORT_HIGHEST_BID -> Comparator.comparing(
                    AuctionSummary::getCurrentPrice,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
            case SORT_NEWEST_START -> Comparator.comparing(
                    AuctionSummary::getStartTime,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
            case SORT_ENDING_SOON -> Comparator
                    .comparingInt((AuctionSummary auction) -> statusSortPriority(AuctionDisplayUtil.displayStatus(auction)))
                    .thenComparing(AuctionSummary::getEndTime, Comparator.nullsLast(LocalDateTime::compareTo));
            default -> Comparator.comparing(
                    AuctionSummary::getEndTime,
                    Comparator.nullsLast(LocalDateTime::compareTo)
            );
        };
        return auctions.stream().sorted(comparator).toList();
    }

    private int statusSortPriority(String status) {
        return switch (status) {
            case "RUNNING" -> 0;
            case "PAID" -> 1;
            case "CANCELED" -> 2;
            default -> 3;
        };
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
