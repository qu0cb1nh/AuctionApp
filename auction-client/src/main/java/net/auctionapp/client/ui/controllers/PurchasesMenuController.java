package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
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
import net.auctionapp.client.ui.controllers.components.AuctionCardController;
import net.auctionapp.client.ui.controllers.components.HeaderController;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.DurationFormatUtil;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.items.ItemType;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.ErrorMessage;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
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

public class PurchasesMenuController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String STATUS_TEXT_STYLE = "-fx-text-fill: #666666;";
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
    private final List<ListingCard> loadedListings = new ArrayList<>();
    private final Set<String> pendingAuctionIds = new HashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("My Listings");
        statusFilterComboBox.getItems().setAll(STATUS_ACTIVE, STATUS_SOLD, STATUS_CANCELED);
        statusFilterComboBox.getSelectionModel().select(STATUS_ACTIVE);
        summaryLabel.setManaged(false);
        summaryLabel.setVisible(false);
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
        pendingAuctionIds.clear();
        allListings.clear();
        loadedListings.clear();
        renderListingCards(List.of(), false);
        showStatus("Loading your listings...", STATUS_TEXT_STYLE);
        AuctionService.getInstance().requestAuctionList(this::handleAuctionListRequestResult);
    }

    private void handleAuctionListRequestResult(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            showStatus("Unexpected response from server.", "-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
            return;
        }
        handleAuctionListResponse(response);
    }

    private void handleAuctionListResponse(AuctionListResponseMessage response) {
        pendingAuctionIds.clear();
        loadedListings.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                .map(summary -> summary == null ? null : summary.getAuctionId())
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (auctionIds.isEmpty()) {
            allListings.clear();
            renderListingCards(List.of(), false);
            showStatus("No auctions available yet.", STATUS_TEXT_STYLE);
            updateSummary();
            return;
        }

        pendingAuctionIds.addAll(auctionIds);
        showStatus("Loading auction details...", STATUS_TEXT_STYLE);
        for (String auctionId : auctionIds) {
            AuctionService.getInstance().requestAuctionDetails(
                    auctionId,
                    detailResponse -> handleAuctionDetailsRequestResult(auctionId, detailResponse)
            );
        }
    }

    private void handleAuctionDetailsRequestResult(String auctionId, Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            completeLoadingAfterDetailFailure(auctionId, errorMessage.getErrorMessage());
            return;
        }
        handleAuctionDetailsResponse(message);
    }

    private void handleAuctionDetailsResponse(Message message) {
        if (!(message instanceof AuctionDetailsResponseMessage response)) {
            return;
        }
        if (!pendingAuctionIds.remove(response.getAuctionId())) {
            return;
        }

        toListingCard(response, resolveCurrentUserId()).ifPresent(loadedListings::add);
        if (!pendingAuctionIds.isEmpty()) {
            showStatus("Loading auction details...", STATUS_TEXT_STYLE);
            return;
        }
        finalizeLoadedListings();
    }

    private void completeLoadingAfterDetailFailure(String auctionId, String errorMessage) {
        if (auctionId != null) {
            pendingAuctionIds.remove(auctionId);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            showStatus(errorMessage, "-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
        }
        if (!pendingAuctionIds.isEmpty()) {
            showStatus("Loading auction details...", STATUS_TEXT_STYLE);
            return;
        }
        finalizeLoadedListings();
    }

    private void finalizeLoadedListings() {
        allListings.clear();
        allListings.addAll(
                loadedListings.stream()
                        .sorted(Comparator.<ListingCard>comparingInt(card -> statusPriority(card.status()))
                                .thenComparing(ListingCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ListingCard::title, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );
        applyFilters();
    }

    private void handleErrorResponse(ErrorMessage errorMessage) {
        showStatus(errorMessage.getErrorMessage(), "-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
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
        if (pendingAuctionIds.isEmpty()) {
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
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            listingFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (ListingCard listing : listings) {
            listingFlowPane.getChildren().add(loadListingCard(listing));
        }
    }

    private HBox loadListingCard(ListingCard listing) {
        boolean active = STATUS_ACTIVE.equals(listing.status());
        boolean canManageAuction = active && ClientSession.getInstance().canManageAuction(listing.sellerId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                listing.imageUrl(),
                listing.itemType(),
                listing.title(),
                "Status: " + listing.status(),
                statusColor(listing.status()),
                "Auction state: " + listing.auctionStatus().name(),
                "Starting price: " + formatMoney(listing.startingPrice()),
                formatTimingLabel(listing.endTime(), listing.auctionStatus()),
                active ? "Current Price" : "Final Price",
                formatMoney(listing.currentPrice()),
                active ? "#0057ff" : "#1f2933",
                "Bids",
                String.valueOf(listing.bidCount()),
                "#1f2933",
                "Winner",
                safeWinnerName(listing.winnerBidderId()),
                "#1f2933",
                "View auction",
                () -> {
            statusLabel.setText("Opening listing: " + listing.title());
            SceneManager.switchToAuctionDetails(listing.auctionId());
        },
                canManageAuction ? "Manage auction" : null,
                canManageAuction ? () -> handleManageAuction(listing.auctionId()) : null
        );
        return loadAuctionCardComponent(cardData);
    }

    private void handleManageAuction(String auctionId) {
        SceneManager.switchToManageAuction(auctionId);
    }

    private HBox loadAuctionCardComponent(AuctionCardController.CardData cardData) {
        try {
            FXMLLoader loader = ResourcesUtil.fxmlLoader("components/AuctionCard.fxml");
            HBox card = loader.load();
            AuctionCardController controller = loader.getController();
            controller.bindCard(cardData);
            return card;
        } catch (IOException | RuntimeException e) {
            Label fallback = new Label("Failed to load listing card.");
            fallback.setStyle("-fx-text-fill: #d9534f;");
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
                response.getStartingPrice(),
                response.getCurrentPrice(),
                bidCount,
                response.getEndTime(),
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
            return response.getWinnerBidderId();
        }
        if (response.getStatus() != AuctionStatus.CANCELED
                && isFinished(response)
                && response.getLeadingBidderId() != null
                && !response.getLeadingBidderId().isBlank()) {
            return response.getLeadingBidderId();
        }
        return null;
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

    private String statusColor(String status) {
        return switch (status) {
            case STATUS_ACTIVE -> "#1f8f4c";
            case STATUS_SOLD -> "#2962ff";
            case STATUS_CANCELED -> "#6b7280";
            default -> "#3f5569";
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

    private String formatTimingLabel(LocalDateTime endTime, AuctionStatus auctionStatus) {
        if (endTime == null) {
            return "End time: N/A";
        }
        if (auctionStatus == AuctionStatus.RUNNING) {
            Duration remaining = Duration.between(LocalDateTime.now(), endTime);
            if (!remaining.isNegative() && !remaining.isZero()) {
                return "Ends in: " + DurationFormatUtil.formatRemainingDuration(remaining);
            }
        }
        return "Ended at: " + CARD_TIME_FORMATTER.format(endTime);
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

    private void showStatus(String text, String style) {
        if (text == null || text.isBlank()) {
            hideStatus();
            return;
        }
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
        statusLabel.setStyle(style);
        statusLabel.setText(text);
    }

    private record ListingCard(
            String auctionId,
            String title,
            String status,
            AuctionStatus auctionStatus,
            String sellerId,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            int bidCount,
            LocalDateTime endTime,
            String winnerBidderId,
            String imageUrl,
            ItemType itemType
    ) {
    }
}
