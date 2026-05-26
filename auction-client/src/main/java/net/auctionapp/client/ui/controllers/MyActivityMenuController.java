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
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.items.ItemType;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.dto.BidView;
import net.auctionapp.common.messages.auction.AuctionDetailsListResponseMessage;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class MyActivityMenuController {
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final String STATUS_ACTIVE = "Active";
    private static final String STATUS_WON = "Won";
    private static final String STATUS_LOST = "Lost";
    private static final String POSITION_LEADING = "Leading";
    private static final String POSITION_OUTBID = "Outbid";
    private static final String POSITION_CLOSED = "Closed";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox activityFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;
    @FXML
    private Label summaryLabel;

    private final List<ActivityCard> allActivities = new ArrayList<>();
    private final Set<String> watchedAuctionIds = new HashSet<>();
    private boolean watchListLoaded;
    private boolean activityLoading;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("My Activities");
        statusFilterComboBox.getItems().setAll(STATUS_ACTIVE, STATUS_WON, STATUS_LOST);
        statusFilterComboBox.getSelectionModel().select(STATUS_ACTIVE);
        FxViewUtil.setVisible(summaryLabel, false);
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
        loadActivities();
    }

    @FXML
    public void handleRefresh() {
        loadActivities();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadActivities() {
        activityLoading = true;
        allActivities.clear();
        renderActivityCards(List.of(), false);
        showStatus("Loading your activities...", false);
        AuctionService.getInstance().requestMyActivity(this::handleActivityRequestResult);
    }

    private void handleActivityRequestResult(Message message) {
        activityLoading = false;
        if (message instanceof ErrorResponseMessage errorMessage) {
            showStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof AuctionDetailsListResponseMessage response)) {
            showStatus("Unexpected response from server.", true);
            return;
        }
        String currentUserId = ClientSession.getInstance().getUserId();
        allActivities.clear();
        allActivities.addAll(
                response.getAuctions().stream()
                        .map(auction -> toActivityCard(auction, currentUserId))
                        .flatMap(Optional::stream)
                        .sorted(Comparator.<ActivityCard>comparingInt(card -> statusPriority(card.status()))
                                .thenComparing(ActivityCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ActivityCard::auctionTitle, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );
        applyFilters();
    }

    private void handleWatchListResponse(Message message) {
        if (!(message instanceof WatchListResponseMessage response)) {
            return;
        }
        watchedAuctionIds.clear();
        response.getAuctions().stream()
                .filter(auction -> auction != null && auction.getAuctionId() != null)
                .map(auction -> auction.getAuctionId())
                .forEach(watchedAuctionIds::add);
        watchListLoaded = true;
        applyFilters();
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();
        if (selectedStatus == null || selectedStatus.isBlank()) {
            selectedStatus = STATUS_ACTIVE;
        }

        String statusFilter = selectedStatus;
        List<ActivityCard> filtered = allActivities.stream()
                .filter(activity -> search.isBlank()
                        || activity.auctionTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(activity -> activity.status().equalsIgnoreCase(statusFilter))
                .toList();

        renderActivityCards(filtered, !allActivities.isEmpty());
        updateSummary();
        if (!activityLoading) {
            showStatus(null, false);
        }
    }

    private void renderActivityCards(List<ActivityCard> activities, boolean hasAnyActivities) {
        activityFlowPane.getChildren().clear();
        if (activities.isEmpty()) {
            String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();
            String statusText = selectedStatus == null || selectedStatus.isBlank()
                    ? STATUS_ACTIVE.toLowerCase(Locale.ROOT)
                    : selectedStatus.toLowerCase(Locale.ROOT);
            Label emptyLabel = new Label(hasAnyActivities
                    ? "No " + statusText + " activities match your current filters."
                    : "You have not placed any bids yet.");
            emptyLabel.getStyleClass().add("empty-state");
            activityFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (ActivityCard activity : activities) {
            activityFlowPane.getChildren().add(loadActivityCard(activity));
        }
    }

    private HBox loadActivityCard(ActivityCard activity) {
        boolean active = STATUS_ACTIVE.equals(activity.status());
        boolean canManageAuction = ClientSession.getInstance().canManageAuction(activity.sellerId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                activity.imageUrl(),
                activity.itemType(),
                activity.auctionTitle(),
                "Owner: " + AuctionDisplayUtil.formatOwner(activity.sellerUsername()),
                AuctionCardController.TextTone.MUTED,
                "Bid state: " + activity.bidPosition(),
                active ? "Ends in: 00:00:00" : "Ended at: " + AuctionDisplayUtil.formatDateTime(activity.endTime()),
                "Bid count: " + activity.bidCount(),
                "Your Max Bid",
                AuctionDisplayUtil.formatPrice(activity.yourMaxBid()),
                AuctionCardController.TextTone.PRIMARY,
                active ? "Current Price" : "Final Price",
                AuctionDisplayUtil.formatPrice(activity.currentPrice()),
                active ? AuctionCardController.TextTone.DANGER : AuctionCardController.TextTone.DEFAULT,
                active ? null : "Winner",
                active ? null : safeWinnerName(activity.winnerBidderId()),
                AuctionCardController.TextTone.DEFAULT,
                "View auction",
                () -> {
            statusLabel.setText("Opening auction: " + activity.auctionTitle());
            SceneManager.switchToAuctionDetails(activity.auctionId());
        },
                canManageAuction ? "Manage auction" : null,
                canManageAuction ? () -> SceneManager.switchToManageAuction(activity.auctionId()) : null,
                watchListLoaded
                        ? AuctionDisplayUtil.watchListButtonText(watchedAuctionIds.contains(activity.auctionId()))
                        : null,
                watchListLoaded ? () -> handleToggleWatchList(activity.auctionId()) : null
        );
        return AuctionCardUtil.createWithDetailCountdown(
                cardData,
                active ? activity.endTime() : null,
                "Failed to load activity card."
        );
    }

    private Optional<ActivityCard> toActivityCard(AuctionDetailsResponseMessage response, String currentUserId) {
        if (response == null || currentUserId == null || currentUserId.isBlank()) {
            return Optional.empty();
        }

        List<BidView> bidHistory = response.getBidHistory();
        List<BidView> userBids = bidHistory.stream()
                .filter(Objects::nonNull)
                .filter(bid -> bid.getBidderId() != null && bid.getBidderId().equalsIgnoreCase(currentUserId))
                .filter(bid -> bid.getAmount() != null)
                .toList();
        if (userBids.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal yourMaxBid = userBids.stream()
                .map(BidView::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return Optional.of(new ActivityCard(
                response.getAuctionId(),
                response.getTitle(),
                deriveActivityStatus(response, currentUserId),
                deriveBidPosition(response, currentUserId),
                response.getSellerId(),
                response.getSellerUsername(),
                yourMaxBid,
                response.getCurrentPrice(),
                bidHistory.size(),
                AuctionDisplayUtil.displayUsername(response.getWinnerBidderUsername(), response.getWinnerBidderId()),
                response.getEndTime(),
                response.getImageUrl(),
                response.getItemType()
        ));
    }

    private String deriveActivityStatus(AuctionDetailsResponseMessage response, String currentUserId) {
        if (isActive(response)) {
            return STATUS_ACTIVE;
        }
        if (currentUserId.equalsIgnoreCase(response.getWinnerBidderId())
                || (isFinished(response) && currentUserId.equalsIgnoreCase(response.getLeadingBidderId()))) {
            return STATUS_WON;
        }
        return STATUS_LOST;
    }

    private String deriveBidPosition(AuctionDetailsResponseMessage response, String currentUserId) {
        if (!isActive(response)) {
            return POSITION_CLOSED;
        }
        return currentUserId.equalsIgnoreCase(response.getLeadingBidderId()) ? POSITION_LEADING : POSITION_OUTBID;
    }

    private int statusPriority(String status) {
        return switch (status) {
            case STATUS_ACTIVE -> 0;
            case STATUS_WON -> 1;
            case STATUS_LOST -> 2;
            default -> 3;
        };
    }

    private String safeWinnerName(String winner) {
        return winner == null || winner.isBlank() ? "No winner" : winner;
    }

    private boolean isActive(AuctionDetailsResponseMessage response) {
        return response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && LocalDateTime.now().isBefore(response.getEndTime());
    }

    private boolean isFinished(AuctionDetailsResponseMessage response) {
        return response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime());
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
            showStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (message instanceof WatchListChangedResponseMessage changed) {
            handleWatchListChanged(changed);
            NotificationToastManager.showSuccess(AuctionDisplayUtil.watchListActionMessage(changed.isWatched()));
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

    private void updateSummary() {
        long activeCount = countByStatus(STATUS_ACTIVE);
        long wonCount = countByStatus(STATUS_WON);
        long lostCount = countByStatus(STATUS_LOST);
        if (allActivities.isEmpty()) {
            summaryLabel.setText("");
            FxViewUtil.setVisible(summaryLabel, false);
            return;
        }
        summaryLabel.setText("Active: " + activeCount + "  Won: " + wonCount + "  Lost: " + lostCount);
        FxViewUtil.setVisible(summaryLabel, true);
    }

    private long countByStatus(String status) {
        return allActivities.stream()
                .filter(activity -> activity.status().equals(status))
                .count();
    }

    private void showStatus(String text, boolean error) {
        boolean visible = text != null && !text.isBlank();
        FxViewUtil.setVisible(statusLabel, visible);
        statusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        statusLabel.setText(visible ? text : "");
    }

    private record ActivityCard(
            String auctionId,
            String auctionTitle,
            String status,
            String bidPosition,
            String sellerId,
            String sellerUsername,
            BigDecimal yourMaxBid,
            BigDecimal currentPrice,
            int bidCount,
            String winnerBidderId,
            LocalDateTime endTime,
            String imageUrl,
            ItemType itemType
    ) {
    }
}
