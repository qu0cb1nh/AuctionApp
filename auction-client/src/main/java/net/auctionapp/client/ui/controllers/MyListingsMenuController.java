package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.services.WatchListService;
import net.auctionapp.client.ui.controllers.components.AuctionCardController;
import net.auctionapp.client.ui.controllers.components.HeaderController;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.items.ItemType;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auction.AuctionDetailsListResponseMessage;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;

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
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

public class MyListingsMenuController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final String STATUS_ACTIVE = "Active";
    private static final String STATUS_SOLD = "Sold";
    private static final String STATUS_CANCELED = "Canceled";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox listingFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;
    @FXML
    private Label summaryLabel;

    private final List<ListingCard> allListings = new ArrayList<>();
    private final Set<String> watchedAuctionIds = new HashSet<>();
    private boolean watchListLoaded;
    private boolean listingsLoading;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("My Listings");
        statusFilterComboBox.getItems().setAll(STATUS_ACTIVE, STATUS_SOLD, STATUS_CANCELED);
        statusFilterComboBox.getSelectionModel().select(STATUS_ACTIVE);
        summaryLabel.setManaged(false);
        summaryLabel.setVisible(false);
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
        requestWatchList();
        loadListings();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadListings();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadListings() {
        listingsLoading = true;
        allListings.clear();
        renderListingCards(List.of(), false);
        showStatus("Loading your listings...", false);
        AuctionService.getInstance().requestMyListings(this::handleListingsRequestResult);
    }

    private void handleListingsRequestResult(Message message) {
        listingsLoading = false;
        if (message instanceof ErrorResponseMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof AuctionDetailsListResponseMessage response)) {
            showStatus("Unexpected response from server.", true);
            return;
        }
        String currentUserId = resolveCurrentUserId();
        allListings.clear();
        allListings.addAll(
                response.getAuctions().stream()
                        .map(auction -> toListingCard(auction, currentUserId))
                        .flatMap(Optional::stream)
                        .sorted(Comparator.<ListingCard>comparingInt(card -> statusPriority(card.status()))
                                .thenComparing(ListingCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ListingCard::title, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );
        applyFilters();
    }

    private void requestWatchList() {
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
    }

    private void handleWatchListResponse(Message message) {
        if (!(message instanceof WatchListResponseMessage response)) {
            return;
        }
        watchedAuctionIds.clear();
        response.getAuctions().stream()
                .filter(auction -> auction != null && auction.getAuctionId() != null)
                .forEach(auction -> watchedAuctionIds.add(auction.getAuctionId()));
        watchListLoaded = true;
        applyFilters();
    }

    private void handleErrorResponse(ErrorResponseMessage errorMessage) {
        showStatus(errorMessage.getErrorMessage(), true);
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();
        if (selectedStatus == null || selectedStatus.isBlank()) {
            selectedStatus = STATUS_ACTIVE;
        }

        String statusFilter = selectedStatus;
        List<ListingCard> filtered = allListings.stream()
                .filter(listing -> search.isBlank() || listing.title().toLowerCase(Locale.ROOT).contains(search))
                .filter(listing -> listing.status().equalsIgnoreCase(statusFilter))
                .toList();

        renderListingCards(filtered, !allListings.isEmpty());
        updateSummary();
        if (!listingsLoading) {
            hideStatus();
        }
    }

    private void renderListingCards(List<ListingCard> listings, boolean hasAnyListings) {
        listingFlowPane.getChildren().clear();
        if (listings.isEmpty()) {
            String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();
            String statusText = selectedStatus == null || selectedStatus.isBlank()
                    ? STATUS_ACTIVE.toLowerCase(Locale.ROOT)
                    : selectedStatus.toLowerCase(Locale.ROOT);
            Label emptyLabel = new Label(hasAnyListings
                    ? "No " + statusText + " listings match your current filters."
                    : "You have not created any listings yet.");
            emptyLabel.getStyleClass().add("empty-state");
            listingFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (ListingCard listing : listings) {
            listingFlowPane.getChildren().add(loadListingCard(listing));
        }
    }

    private HBox loadListingCard(ListingCard listing) {
        boolean active = STATUS_ACTIVE.equals(listing.status());
        boolean canManageAuction = ClientSession.getInstance().canManageAuction(listing.sellerId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                listing.imageUrl(),
                listing.itemType(),
                listing.title(),
                "Owner: " + formatOwner(listing.sellerUsername()),
                AuctionCardController.TextTone.MUTED,
                "Auction state: " + listing.auctionStatus().name(),
                "Starting price: " + formatMoney(listing.startingPrice()),
                null,
                active ? "Current Price" : "Final Price",
                formatMoney(listing.currentPrice()),
                active ? AuctionCardController.TextTone.PRIMARY : AuctionCardController.TextTone.DEFAULT,
                active ? "Ends In" : "Ended At",
                active ? "00:00:00" : formatDateTime(listing.endTime()),
                AuctionCardController.TextTone.DEFAULT,
                bidderCaption(listing),
                bidderValue(listing),
                AuctionCardController.TextTone.DEFAULT,
                "View auction",
                () -> {
            statusLabel.setText("Opening listing: " + listing.title());
            SceneManager.switchToAuctionDetails(listing.auctionId());
        },
                canManageAuction ? "Manage auction" : null,
                canManageAuction ? () -> handleManageAuction(listing.auctionId()) : null,
                watchListLoaded ? formatWatchListButtonText(listing.auctionId()) : null,
                watchListLoaded ? () -> handleToggleWatchList(listing.auctionId()) : null
        );
        return loadAuctionCardComponent(cardData, active ? listing.endTime() : null);
    }

    private void handleManageAuction(String auctionId) {
        SceneManager.switchToManageAuction(auctionId);
    }

    private HBox loadAuctionCardComponent(AuctionCardController.CardData cardData, LocalDateTime countdownEndTime) {
        try {
            FXMLLoader loader = ResourcesUtil.fxmlLoader("components/AuctionCard.fxml");
            HBox card = loader.load();
            AuctionCardController controller = loader.getController();
            controller.bindCard(cardData);
            controller.startMetricTwoCountdown(countdownEndTime);
            return card;
        } catch (IOException | RuntimeException e) {
            Label fallback = new Label("Failed to load listing card.");
            fallback.getStyleClass().add("load-error");
            return new HBox(fallback);
        }
    }

    private Optional<ListingCard> toListingCard(AuctionDetailsResponseMessage response, String currentUserId) {
        if (response == null || currentUserId == null || currentUserId.isBlank()) {
            return Optional.empty();
        }
        if (response.getSellerId() == null || !response.getSellerId().equalsIgnoreCase(currentUserId)) {
            return Optional.empty();
        }

        int bidCount = response.getBidHistory() == null ? 0 : response.getBidHistory().size();
        return Optional.of(new ListingCard(
                response.getAuctionId(),
                response.getTitle(),
                deriveListingStatus(response, bidCount),
                response.getStatus(),
                response.getSellerId(),
                response.getSellerUsername(),
                response.getStartingPrice(),
                response.getCurrentPrice(),
                bidCount,
                response.getEndTime(),
                displayUsername(response.getLeadingBidderUsername(), response.getLeadingBidderId()),
                resolveWinner(response),
                response.getImageUrl(),
                response.getItemType()
        ));
    }

    private String resolveCurrentUserId() {
        if (ClientSession.getInstance().getUserId() == null || ClientSession.getInstance().getUserId().isBlank()) {
            return "";
        }
        return ClientSession.getInstance().getUserId();
    }

    private String deriveListingStatus(AuctionDetailsResponseMessage response, int bidCount) {
        if (response.getStatus() == AuctionStatus.CANCELED) {
            return STATUS_CANCELED;
        }
        if (isActive(response)) {
            return STATUS_ACTIVE;
        }
        if (hasWinner(response) || response.getStatus() == AuctionStatus.RUNNING && isEnded(response) && bidCount > 0) {
            return STATUS_SOLD;
        }
        return STATUS_CANCELED;
    }

    private String resolveWinner(AuctionDetailsResponseMessage response) {
        if (response.getWinnerBidderId() != null && !response.getWinnerBidderId().isBlank()) {
            return displayUsername(response.getWinnerBidderUsername(), response.getWinnerBidderId());
        }
        if (response.getStatus() != AuctionStatus.CANCELED
                && isFinished(response)
                && response.getLeadingBidderId() != null
                && !response.getLeadingBidderId().isBlank()) {
            return displayUsername(response.getLeadingBidderUsername(), response.getLeadingBidderId());
        }
        return null;
    }

    private String displayUsername(String username, String userId) {
        return username == null || username.isBlank() ? userId : username;
    }

    private boolean hasWinner(AuctionDetailsResponseMessage response) {
        return response != null
                && response.getWinnerBidderId() != null
                && !response.getWinnerBidderId().isBlank();
    }

    private int statusPriority(String status) {
        return switch (status) {
            case STATUS_ACTIVE -> 0;
            case STATUS_SOLD -> 1;
            case STATUS_CANCELED -> 2;
            default -> 3;
        };
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }
        return "$" + amount.stripTrailingZeros().toPlainString();
    }

    private String safeWinnerName(String winner) {
        if (winner == null || winner.isBlank()) {
            return "No winner";
        }
        return winner;
    }

    private String bidderCaption(ListingCard listing) {
        if (listing.auctionStatus() == AuctionStatus.PAID || STATUS_CANCELED.equals(listing.status())) {
            return "Winner";
        }
        return "Top Bidder";
    }

    private String bidderValue(ListingCard listing) {
        if (STATUS_CANCELED.equals(listing.status())) {
            return "No winner";
        }
        if (listing.auctionStatus() == AuctionStatus.PAID) {
            return safeWinnerName(listing.winnerBidderName());
        }
        return listing.leadingBidderName() == null || listing.leadingBidderName().isBlank()
                ? "No bids yet"
                : listing.leadingBidderName();
    }

    private String formatOwner(String sellerUsername) {
        return sellerUsername == null || sellerUsername.isBlank() ? "Unknown" : sellerUsername;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "N/A" : CARD_TIME_FORMATTER.format(time);
    }

    private boolean isActive(AuctionDetailsResponseMessage response) {
        return response != null
                && response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && LocalDateTime.now().isBefore(response.getEndTime());
    }

    private boolean isFinished(AuctionDetailsResponseMessage response) {
        return response != null
                && (response.getStatus() == AuctionStatus.PAID
                || response.getStatus() == AuctionStatus.CANCELED
                || response.getStatus() == AuctionStatus.RUNNING && isEnded(response));
    }

    private boolean isEnded(AuctionDetailsResponseMessage response) {
        return response != null
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime());
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
        if (message instanceof WatchListChangedResponseMessage changed) {
            handleWatchListChanged(changed);
            NotificationToastManager.showSuccess(watchListActionMessage(changed));
        }
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

    private String watchListActionMessage(WatchListChangedResponseMessage changed) {
        return changed.isWatched()
                ? "Auction added to your watchlist."
                : "Auction removed from your watchlist.";
    }

    private String formatWatchListButtonText(String auctionId) {
        return watchedAuctionIds.contains(auctionId) ? "Watching" : "Add to watchlist";
    }

    private void updateSummary() {
        long activeCount = countByStatus(STATUS_ACTIVE);
        long soldCount = countByStatus(STATUS_SOLD);
        long canceledCount = countByStatus(STATUS_CANCELED);
        if (allListings.isEmpty()) {
            summaryLabel.setText("");
            summaryLabel.setManaged(false);
            summaryLabel.setVisible(false);
            return;
        }
        summaryLabel.setText("Active: " + activeCount + "  Sold: " + soldCount + "  Canceled: " + canceledCount);
        summaryLabel.setManaged(true);
        summaryLabel.setVisible(true);
    }

    private long countByStatus(String status) {
        return allListings.stream()
                .filter(listing -> listing.status().equals(status))
                .count();
    }

    private void hideStatus() {
        statusLabel.setText("");
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);
    }

    private void showStatus(String text, boolean error) {
        if (text == null || text.isBlank()) {
            hideStatus();
            return;
        }
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
        statusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        statusLabel.setText(text);
    }

    private record ListingCard(
            String auctionId,
            String title,
            String status,
            AuctionStatus auctionStatus,
            String sellerId,
            String sellerUsername,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            int bidCount,
            LocalDateTime endTime,
            String leadingBidderName,
            String winnerBidderName,
            String imageUrl,
            ItemType itemType
    ) {
    }
}
