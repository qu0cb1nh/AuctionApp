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
import net.auctionapp.common.dto.ActivitySummaryDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auction.MyActivityResponseMessage;
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

public class MyActivityMenuController {
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final String STATUS_ACTIVE = "Active";
    private static final String STATUS_WON = "Won";
    private static final String STATUS_LOST = "Lost";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox activityFlowPane;
    @FXML
    private AuctionToolBarController auctionToolBarController;
    @FXML
    private Label statusLabel;
    @FXML
    private Label summaryLabel;

    private final List<ActivitySummaryDto> allActivities = new ArrayList<>();
    private final Set<String> watchedAuctionIds = new HashSet<>();
    private boolean watchListLoaded;
    private boolean activityLoading;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("My activities");
        auctionToolBarController.setup(
                "Search your activities...",
                List.of(STATUS_ACTIVE, STATUS_WON, STATUS_LOST),
                STATUS_ACTIVE,
                this::applyFilters,
                this::loadActivities
        );
        FxViewUtil.setVisible(summaryLabel, false);
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
        loadActivities();
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
        if (!(message instanceof MyActivityResponseMessage response)) {
            showStatus("Unexpected response from server.", true);
            return;
        }
        allActivities.clear();
        allActivities.addAll(
                response.getActivities().stream()
                        .sorted(Comparator.<ActivitySummaryDto>comparingInt(activity -> statusPriority(activity.getStatus()))
                                .thenComparing(ActivitySummaryDto::getEndTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ActivitySummaryDto::getTitle, String.CASE_INSENSITIVE_ORDER))
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
        String search = auctionToolBarController.getSearchText();
        String selectedStatus = auctionToolBarController.getSelectedFilter();
        if (selectedStatus == null || selectedStatus.isBlank()) {
            selectedStatus = STATUS_ACTIVE;
        }

        String statusFilter = selectedStatus;
        List<ActivitySummaryDto> filtered = allActivities.stream()
                .filter(activity -> search.isBlank()
                        || activity.getTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(activity -> activity.getStatus().equalsIgnoreCase(statusFilter))
                .toList();

        renderActivityCards(filtered, !allActivities.isEmpty());
        updateSummary();
        if (!activityLoading) {
            showStatus(null, false);
        }
    }

    private void renderActivityCards(List<ActivitySummaryDto> activities, boolean hasAnyActivities) {
        activityFlowPane.getChildren().clear();
        if (activities.isEmpty()) {
            String selectedStatus = auctionToolBarController.getSelectedFilter();
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

        for (ActivitySummaryDto activity : activities) {
            activityFlowPane.getChildren().add(loadActivityCard(activity));
        }
    }

    private HBox loadActivityCard(ActivitySummaryDto activity) {
        boolean active = STATUS_ACTIVE.equals(activity.getStatus());
        boolean canManageAuction = ClientSession.getInstance().canManageAuction(activity.getSellerId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                activity.getImageUrl(),
                activity.getItemType(),
                activity.getTitle(),
                "Owner: " + AuctionDisplayUtil.formatOwner(activity.getSellerUsername()),
                AuctionCardController.TextTone.MUTED,
                "Bid state: " + activity.getBidPosition(),
                null,
                "Bid count: " + activity.getBidCount(),
                active ? "Current price" : "Final price",
                AuctionDisplayUtil.formatPrice(activity.getCurrentPrice()),
                active ? AuctionCardController.TextTone.DANGER : AuctionCardController.TextTone.DEFAULT,
                active ? "Your max bid" : "Winner",
                active ? AuctionDisplayUtil.formatPrice(activity.getYourMaxBid()) : safeWinnerName(activity.getWinnerBidderName()),
                active ? AuctionCardController.TextTone.PRIMARY : AuctionCardController.TextTone.DEFAULT,
                active ? "Ends in" : "Ended at",
                active ? "00:00:00" : AuctionDisplayUtil.formatDateTime(activity.getEndTime()),
                AuctionCardController.TextTone.DEFAULT,
                "View auction",
                () -> {
            statusLabel.setText("Opening auction: " + activity.getTitle());
            SceneManager.switchToAuctionDetails(activity.getAuctionId());
        },
                canManageAuction ? "Manage auction" : null,
                canManageAuction ? () -> SceneManager.switchToManageAuction(activity.getAuctionId()) : null,
                watchListLoaded
                        ? AuctionDisplayUtil.watchListButtonText(watchedAuctionIds.contains(activity.getAuctionId()))
                        : null,
                watchListLoaded ? () -> handleToggleWatchList(activity.getAuctionId()) : null
        );
        return AuctionCardUtil.createWithMetricCountdown(
                cardData,
                active ? activity.getEndTime() : null,
                "Failed to load activity card."
        );
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
                .filter(activity -> activity.getStatus().equals(status))
                .count();
    }

    private void showStatus(String text, boolean error) {
        boolean visible = text != null && !text.isBlank();
        FxViewUtil.setVisible(statusLabel, visible);
        statusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        statusLabel.setText(visible ? text : "");
    }
}
