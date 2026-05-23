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
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.BidView;
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
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

public class MyActivityMenuController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String STATUS_TEXT_STYLE = "-fx-text-fill: #666666;";
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
    private final List<ActivityCard> loadedActivities = new ArrayList<>();
    private final Set<String> pendingAuctionIds = new HashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("My Activities");
        statusFilterComboBox.getItems().setAll(STATUS_ACTIVE, STATUS_WON, STATUS_LOST);
        statusFilterComboBox.getSelectionModel().select(STATUS_ACTIVE);
        summaryLabel.setManaged(false);
        summaryLabel.setVisible(false);
        loadActivities();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadActivities();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadActivities() {
        pendingAuctionIds.clear();
        allActivities.clear();
        loadedActivities.clear();
        renderActivityCards(List.of(), false);
        showStatus("Loading your activities...", STATUS_TEXT_STYLE);
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
        loadedActivities.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                .map(summary -> summary == null ? null : summary.getAuctionId())
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (auctionIds.isEmpty()) {
            allActivities.clear();
            renderActivityCards(List.of(), false);
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

        toActivityCard(response, resolveCurrentUserId()).ifPresent(loadedActivities::add);
        if (!pendingAuctionIds.isEmpty()) {
            showStatus("Loading auction details...", STATUS_TEXT_STYLE);
            return;
        }
        finalizeLoadedActivities();
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
        finalizeLoadedActivities();
    }

    private void finalizeLoadedActivities() {
        allActivities.clear();
        allActivities.addAll(
                loadedActivities.stream()
                        .sorted(Comparator.<ActivityCard>comparingInt(card -> statusPriority(card.status()))
                                .thenComparing(ActivityCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ActivityCard::auctionTitle, String.CASE_INSENSITIVE_ORDER))
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
        List<ActivityCard> filtered = allActivities.stream()
                .filter(activity -> search.isBlank()
                        || activity.auctionTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(activity -> activity.status().equalsIgnoreCase(statusFilter))
                .toList();

        renderActivityCards(filtered, !allActivities.isEmpty());
        updateSummary();
        if (pendingAuctionIds.isEmpty()) {
            hideStatus();
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
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            activityFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (ActivityCard activity : activities) {
            activityFlowPane.getChildren().add(loadActivityCard(activity));
        }
    }

    private HBox loadActivityCard(ActivityCard activity) {
        boolean active = STATUS_ACTIVE.equals(activity.status());
        String metricThreeCaption = active ? "Minimum Next" : "Winner";
        String metricThreeValue = active ? formatMoney(activity.minimumNextBid()) : safeWinnerName(activity.winnerBidderId());
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                activity.imageUrl(),
                activity.auctionTitle(),
                "Status: " + activity.status(),
                statusColor(activity.status()),
                "Bid state: " + activity.bidPosition(),
                formatTimingLabel(activity.endTime(), activity.auctionStatus()),
                "Bid count: " + activity.bidCount(),
                "Your Max Bid",
                formatMoney(activity.yourMaxBid()),
                "#0057ff",
                active ? "Current Price" : "Final Price",
                formatMoney(activity.currentPrice()),
                active ? "#e03621" : "#1f2933",
                metricThreeCaption,
                metricThreeValue,
                "#1f2933",
                buttonLabelForActivity(activity),
                () -> {
            statusLabel.setText("Opening auction: " + activity.auctionTitle());
            SceneManager.switchToAuctionDetails(activity.auctionId());
        },
                null,
                null
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
            Label fallback = new Label("Failed to load activity card.");
            fallback.setStyle("-fx-text-fill: #d9534f;");
            return new HBox(fallback);
        }
    }

    private Optional<ActivityCard> toActivityCard(AuctionDetailsResponseMessage response, String currentUserId) {
        if (response == null || currentUserId == null || currentUserId.isBlank()) {
            return Optional.empty();
        }

        List<BidView> bidHistory = response.getBidHistory() == null ? List.of() : response.getBidHistory();
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
                response.getStatus(),
                yourMaxBid,
                response.getCurrentPrice(),
                response.getMinimumNextBid(),
                bidHistory.size(),
                response.getWinnerBidderId(),
                response.getEndTime(),
                response.getImageUrl()
        ));
    }

    private String resolveCurrentUserId() {
        if (ClientSession.getInstance().getUserId() == null
                || ClientSession.getInstance().getUserId().isBlank()) {
            return "";
        }
        return ClientSession.getInstance().getUserId();
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

    private String statusColor(String status) {
        return switch (status) {
            case STATUS_ACTIVE, STATUS_WON -> "#1f8f4c";
            case STATUS_LOST -> "#c13c21";
            default -> "#3f5569";
        };
    }

    private String buttonLabelForActivity(ActivityCard activity) {
        if (STATUS_ACTIVE.equals(activity.status()) && POSITION_OUTBID.equals(activity.bidPosition())) {
            return "Bid again";
        }
        return "View auction";
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
                && response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime());
    }

    private void updateSummary() {
        long activeCount = countByStatus(STATUS_ACTIVE);
        long wonCount = countByStatus(STATUS_WON);
        long lostCount = countByStatus(STATUS_LOST);
        if (allActivities.isEmpty()) {
            summaryLabel.setText("");
            summaryLabel.setManaged(false);
            summaryLabel.setVisible(false);
            return;
        }
        summaryLabel.setText("Active: " + activeCount + "  Won: " + wonCount + "  Lost: " + lostCount);
        summaryLabel.setManaged(true);
        summaryLabel.setVisible(true);
    }

    private long countByStatus(String status) {
        return allActivities.stream()
                .filter(activity -> activity.status().equals(status))
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

    private record ActivityCard(
            String auctionId,
            String auctionTitle,
            String status,
            String bidPosition,
            AuctionStatus auctionStatus,
            BigDecimal yourMaxBid,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            int bidCount,
            String winnerBidderId,
            LocalDateTime endTime,
            String imageUrl
    ) {
    }
}
