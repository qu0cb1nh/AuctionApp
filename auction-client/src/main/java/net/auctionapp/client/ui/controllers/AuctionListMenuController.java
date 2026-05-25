package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.AuctionCardController;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.services.WatchListService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.dto.AuctionSummary;
import net.auctionapp.common.messages.auction.AuctionListResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.auction.AuctionStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

public class AuctionListMenuController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
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
    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            showListStatus("Unexpected response from server.", true);
            return;
        }

        List<AuctionSummary> auctions = response.getAuctions();
        if (auctions == null) {
            auctions = List.of();
        }
        allAuctions.clear();
        allAuctions.addAll(auctions);
        applyFilters();
    }

    private void handleErrorResponse(ErrorResponseMessage errorMessage) {
        showListStatus(errorMessage.getErrorMessage(), true);
    }

    private void requestWatchList() {
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
    }

    private void handleWatchListResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof WatchListResponseMessage response)) {
            showListStatus("Unexpected watch list response from server.", true);
            return;
        }
        watchedAuctionIds.clear();
        for (AuctionSummary auction : response.getAuctions()) {
            if (auction != null && auction.getAuctionId() != null) {
                watchedAuctionIds.add(auction.getAuctionId());
            }
        }
        watchListLoaded = true;
        applyFilters();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
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
                        || deriveDisplayStatus(auction).equalsIgnoreCase(selectedStatus))
                .toList();

        List<AuctionSummary> sorted = sortAuctions(filtered, selectedSort);
        renderAuctionCards(sorted);
        showListStatus(null, false);
        if (allAuctions.isEmpty()) {
            showListStatus("No auctions available.", false);
            return;
        }
        if (filtered.isEmpty()) {
            showListStatus("No auctions match your search/filter.", false);
            return;
        }
        hideListStatus();
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
                () -> handleViewItem(auction.getAuctionId()),
                canManageAuction ? "Manage auction" : null,
                canManageAuction ? () -> handleManageAuction(auction.getAuctionId()) : null,
                authenticatedUser && watchListLoaded ? formatWatchListButtonText(auction.getAuctionId()) : null,
                authenticatedUser && watchListLoaded ? () -> handleToggleWatchList(auction.getAuctionId()) : null
        );
        return loadAuctionCardComponent(cardData);
    }

    private HBox loadAuctionCardComponent(AuctionCardController.CardData cardData) {
        try {
            FXMLLoader loader = ResourcesUtil.fxmlLoader("components/AuctionCard.fxml");
            HBox card = loader.load();
            AuctionCardController controller = loader.getController();
            controller.bindCard(cardData);
            return card;
        } catch (IOException | RuntimeException e) {
            Label fallback = new Label("Failed to load auction card.");
            fallback.getStyleClass().add("load-error");
            return new HBox(fallback);
        }
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
                    .comparingInt((AuctionSummary auction) -> statusSortPriority(deriveDisplayStatus(auction)))
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

    private String formatPrice(BigDecimal value) {
        if (value == null) {
            return "N/A";
        }
        return "$" + value.stripTrailingZeros().toPlainString();
    }

    private String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "N/A";
        }
        return CARD_TIME_FORMATTER.format(time);
    }

    private String formatTopBidder(String leadingBidderId) {
        if (leadingBidderId == null || leadingBidderId.isBlank()) {
            return "No bids yet";
        }
        return leadingBidderId;
    }

    private String formatOwner(String sellerUsername) {
        return sellerUsername == null || sellerUsername.isBlank() ? "Unknown" : sellerUsername;
    }

    private String deriveDisplayStatus(AuctionSummary auction) {
        if (auction == null || auction.getStatus() == null) {
            return "N/A";
        }
        if (auction.getStatus() == AuctionStatus.CANCELED) {
            return "CANCELED";
        }
        if (auction.getStatus() == AuctionStatus.PAID) {
            return "PAID";
        }
        if (auction.getEndTime() != null && !LocalDateTime.now().isBefore(auction.getEndTime())) {
            return formatClosedStatus(auction.getLeadingBidderId());
        }
        return "RUNNING";
    }

    private String formatClosedStatus(String leadingBidderId) {
        return leadingBidderId == null || leadingBidderId.isBlank() ? "CANCELED" : "PAID";
    }

    private void handleViewItem(String auctionId) {
        SceneManager.switchToAuctionDetails(auctionId);
    }

    private void handleManageAuction(String auctionId) {
        SceneManager.switchToManageAuction(auctionId);
    }

    private void handleToggleWatchList(String auctionId) {
        boolean targetState = !watchedAuctionIds.contains(auctionId);
        WatchListService.getInstance().updateWatched(auctionId, targetState, this::handleWatchListUpdateResponse);
    }

    private void handleWatchListUpdateResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof WatchListChangedResponseMessage changed)) {
            showListStatus("Unexpected watch list response from server.", true);
            return;
        }
        applyWatchListChange(changed);
    }

    private void handleWatchListChanged(WatchListChangedResponseMessage changed) {
        applyWatchListChange(changed);
    }

    private void applyWatchListChange(WatchListChangedResponseMessage changed) {
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

    private String formatWatchListButtonText(String auctionId) {
        return watchedAuctionIds.contains(auctionId) ? "Watching" : "Add to watchlist";
    }

    private void hideListStatus() {
        listStatusLabel.setText("");
        listStatusLabel.setManaged(false);
        listStatusLabel.setVisible(false);
    }

    private void showListStatus(String text, boolean error) {
        if (text == null || text.isBlank()) {
            hideListStatus();
            return;
        }
        listStatusLabel.setManaged(true);
        listStatusLabel.setVisible(true);
        listStatusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        listStatusLabel.setText(text);
    }

}
