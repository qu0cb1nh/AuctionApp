package net.auctionapp.client.ui.controllers;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
import net.auctionapp.common.dto.ListingSummaryDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auction.MyListingsResponseMessage;
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

public class MyListingsMenuController {
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final String STATUS_ACTIVE = "Active";
    private static final String STATUS_SOLD = "Sold";
    private static final String STATUS_CANCELED = "Canceled";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private VBox listingFlowPane;
    @FXML
    private AuctionToolBarController auctionToolBarController;
    @FXML
    private Label statusLabel;
    @FXML
    private Label summaryLabel;

    private final List<ListingSummaryDto> allListings = new ArrayList<>();
    private final Set<String> watchedAuctionIds = new HashSet<>();
    private boolean watchListLoaded;
    private boolean listingsLoading;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("My Listings");
        auctionToolBarController.setup(
                "Search your listings...",
                List.of(STATUS_ACTIVE, STATUS_SOLD, STATUS_CANCELED),
                STATUS_ACTIVE,
                this::applyFilters,
                this::loadListings
        );
        FxViewUtil.setVisible(summaryLabel, false);
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
        loadListings();
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
            showStatus(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof MyListingsResponseMessage response)) {
            showStatus("Unexpected response from server.", true);
            return;
        }
        allListings.clear();
        allListings.addAll(
                response.getListings().stream()
                        .sorted(Comparator.<ListingSummaryDto>comparingInt(listing -> statusPriority(listing.getStatus()))
                                .thenComparing(ListingSummaryDto::getEndTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ListingSummaryDto::getTitle, String.CASE_INSENSITIVE_ORDER))
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
        List<ListingSummaryDto> filtered = allListings.stream()
                .filter(listing -> search.isBlank() || listing.getTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(listing -> listing.getStatus().equalsIgnoreCase(statusFilter))
                .toList();

        renderListingCards(filtered, !allListings.isEmpty());
        updateSummary();
        if (!listingsLoading) {
            showStatus(null, false);
        }
    }

    private void renderListingCards(List<ListingSummaryDto> listings, boolean hasAnyListings) {
        listingFlowPane.getChildren().clear();
        if (listings.isEmpty()) {
            String selectedStatus = auctionToolBarController.getSelectedFilter();
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

        for (ListingSummaryDto listing : listings) {
            listingFlowPane.getChildren().add(loadListingCard(listing));
        }
    }

    private HBox loadListingCard(ListingSummaryDto listing) {
        boolean active = STATUS_ACTIVE.equals(listing.getStatus());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                listing.getImageUrl(),
                listing.getItemType(),
                listing.getTitle(),
                "Owner: " + AuctionDisplayUtil.formatOwner(listing.getSellerUsername()),
                AuctionCardController.TextTone.MUTED,
                "Auction state: " + listing.getStatus(),
                null,
                null,
                active ? "Current Price" : "Final Price",
                AuctionDisplayUtil.formatPrice(listing.getCurrentPrice()),
                active ? AuctionCardController.TextTone.PRIMARY : AuctionCardController.TextTone.DEFAULT,
                active ? "Ends In" : "Ended At",
                active ? "00:00:00" : AuctionDisplayUtil.formatDateTime(listing.getEndTime()),
                AuctionCardController.TextTone.DEFAULT,
                listing.getBidderCaption(),
                listing.getBidderValue(),
                AuctionCardController.TextTone.DEFAULT,
                "View auction",
                () -> {
            statusLabel.setText("Opening listing: " + listing.getTitle());
            SceneManager.switchToAuctionDetails(listing.getAuctionId());
                },
                "Manage auction",
                () -> SceneManager.switchToManageAuction(listing.getAuctionId()),
                watchListLoaded
                        ? AuctionDisplayUtil.watchListButtonText(watchedAuctionIds.contains(listing.getAuctionId()))
                        : null,
                watchListLoaded ? () -> handleToggleWatchList(listing.getAuctionId()) : null
        );
        return AuctionCardUtil.createWithMetricCountdown(
                cardData,
                active ? listing.getEndTime() : null,
                "Failed to load listing card."
        );
    }

    private int statusPriority(String status) {
        return switch (status) {
            case STATUS_ACTIVE -> 0;
            case STATUS_SOLD -> 1;
            case STATUS_CANCELED -> 2;
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
        long soldCount = countByStatus(STATUS_SOLD);
        long canceledCount = countByStatus(STATUS_CANCELED);
        if (allListings.isEmpty()) {
            summaryLabel.setText("");
            FxViewUtil.setVisible(summaryLabel, false);
            return;
        }
        summaryLabel.setText("Active: " + activeCount + "  Sold: " + soldCount + "  Canceled: " + canceledCount);
        FxViewUtil.setVisible(summaryLabel, true);
    }

    private long countByStatus(String status) {
        return allListings.stream()
                .filter(listing -> listing.getStatus().equals(status))
                .count();
    }

    private void showStatus(String text, boolean error) {
        boolean visible = text != null && !text.isBlank();
        FxViewUtil.setVisible(statusLabel, visible);
        statusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        statusLabel.setText(visible ? text : "");
    }
}
