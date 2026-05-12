package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.utils.DurationFormatUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.BidView;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.models.auction.AuctionStatus;

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

public class MyActivityController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SCOPE_BIDDING = "BIDDING";
    private static final String SCOPE_SELLING = "SELLING";

    private static final String STATUS_ALL = "All";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LEADING = "LEADING";
    private static final String STATUS_OUTBID = "OUTBID";
    private static final String STATUS_WON = "WON";
    private static final String STATUS_LOST = "LOST";
    private static final String STATUS_SOLD = "SOLD";
    private static final String STATUS_UNSOLD = "UNSOLD";
    private static final String STATUS_CANCELED = "CANCELED";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private BorderPane rootPane;
    @FXML
    private FlowPane bidActivityFlowPane;
    @FXML
    private FlowPane sellerActivityFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;
    @FXML
    private Label bidSectionLabel;
    @FXML
    private Label sellerSectionLabel;
    @FXML
    private Label bidsNavLabel;
    @FXML
    private Label auctionsNavLabel;
    @FXML
    private VBox bidPageBox;
    @FXML
    private VBox sellerPageBox;

    private final List<ActivityCard> allBidActivities = new ArrayList<>();
    private final List<ActivityCard> loadedBidActivities = new ArrayList<>();
    private final List<ActivityCard> allSellerActivities = new ArrayList<>();
    private final List<ActivityCard> loadedSellerActivities = new ArrayList<>();
    private final Set<String> pendingAuctionIds = new HashSet<>();
    private ActivitySection activeSection = ActivitySection.BIDS;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Activity", true, "MainMenu");

        statusFilterComboBox.getItems().setAll(
                STATUS_ALL,
                STATUS_ACTIVE,
                STATUS_LEADING,
                STATUS_OUTBID,
                STATUS_WON,
                STATUS_LOST,
                STATUS_SOLD,
                STATUS_UNSOLD,
                STATUS_CANCELED
        );
        statusFilterComboBox.getSelectionModel().selectFirst();
        setActiveSection(ActivitySection.BIDS);
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                // No persistent request handlers to clean up.
            }
        });

        loadActivity();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadActivity();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    @FXML
    public void handleClearFilters(ActionEvent event) {
        searchField.clear();
        statusFilterComboBox.getSelectionModel().selectFirst();
        applyFilters();
    }

    @FXML
    public void handleShowBidPage(MouseEvent event) {
        setActiveSection(ActivitySection.BIDS);
    }

    @FXML
    public void handleShowAuctionPage(MouseEvent event) {
        setActiveSection(ActivitySection.AUCTIONS);
    }

    private void loadActivity() {
        pendingAuctionIds.clear();
        allBidActivities.clear();
        loadedBidActivities.clear();
        allSellerActivities.clear();
        loadedSellerActivities.clear();
        renderBidCards(List.of(), false);
        renderSellerCards(List.of(), false);
        setNeutralStatus("Loading your activity...");
        AuctionService.getInstance().requestAuctionList(this::handleAuctionListRequestResult);
    }

    private void handleAuctionListRequestResult(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            setErrorStatus("Unexpected response from server.");
            return;
        }
        handleAuctionListResponse(response);
    }

    private void handleAuctionListResponse(AuctionListResponseMessage response) {
        pendingAuctionIds.clear();
        loadedBidActivities.clear();
        loadedSellerActivities.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                .map(summary -> summary == null ? null : summary.getAuctionId())
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (auctionIds.isEmpty()) {
            allBidActivities.clear();
            allSellerActivities.clear();
            renderBidCards(List.of(), false);
            renderSellerCards(List.of(), false);
            setNeutralStatus("No auctions available yet.");
            return;
        }

        pendingAuctionIds.addAll(auctionIds);
        setNeutralStatus("Loading auction details...");
        for (String auctionId : auctionIds) {
            AuctionService.getInstance().requestAuctionDetails(
                    auctionId,
                    (detailResponse) -> handleAuctionDetailsRequestResult(auctionId, detailResponse)
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

        String currentUsername = resolveCurrentUsername();
        toBidActivity(response, currentUsername).ifPresent(loadedBidActivities::add);
        toSellerActivity(response, currentUsername).ifPresent(loadedSellerActivities::add);

        if (!pendingAuctionIds.isEmpty()) {
            setNeutralStatus("Loading auction details...");
            return;
        }

        finalizeLoadedActivities();
    }

    private void completeLoadingAfterDetailFailure(String auctionId, String errorMessage) {
        if (auctionId != null) {
            pendingAuctionIds.remove(auctionId);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            setErrorStatus(errorMessage);
        }
        if (!pendingAuctionIds.isEmpty()) {
            setNeutralStatus("Loading auction details...");
            return;
        }
        finalizeLoadedActivities();
    }

    private void finalizeLoadedActivities() {
        allBidActivities.clear();
        allBidActivities.addAll(
                loadedBidActivities.stream()
                        .sorted(Comparator.<ActivityCard>comparingInt(card -> statusPriority(card.status()))
                                .thenComparing(ActivityCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ActivityCard::auctionTitle, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );

        allSellerActivities.clear();
        allSellerActivities.addAll(
                loadedSellerActivities.stream()
                        .sorted(Comparator.<ActivityCard>comparingInt(card -> statusPriority(card.status()))
                                .thenComparing(ActivityCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(ActivityCard::auctionTitle, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );

        applyFilters();
    }

    private void handleErrorResponse(ErrorMessage errorMessage) {
        setErrorStatus(errorMessage.getErrorMessage());
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();

        List<ActivityCard> filteredBids = allBidActivities.stream()
                .filter(activity -> search.isBlank()
                        || activity.auctionTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(activity -> matchesStatusFilter(activity.status(), selectedStatus))
                .toList();

        List<ActivityCard> filteredSellerAuctions = allSellerActivities.stream()
                .filter(activity -> search.isBlank()
                        || activity.auctionTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(activity -> matchesStatusFilter(activity.status(), selectedStatus))
                .toList();

        renderBidCards(filteredBids, !allBidActivities.isEmpty());
        renderSellerCards(filteredSellerAuctions, !allSellerActivities.isEmpty());
        bidSectionLabel.setText("Your Bids");
        sellerSectionLabel.setText("Your Auctions");
        updateSectionSummary();
    }

    private boolean matchesStatusFilter(String status, String selectedStatus) {
        return selectedStatus == null
                || STATUS_ALL.equals(selectedStatus)
                || selectedStatus.equalsIgnoreCase(status);
    }

    private void renderBidCards(List<ActivityCard> activities, boolean hasAnyActivities) {
        bidActivityFlowPane.getChildren().clear();
        if (activities.isEmpty()) {
            Label emptyLabel = new Label(hasAnyActivities
                    ? "No bid activity matches your current filters."
                    : "You have not placed any bids yet.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            bidActivityFlowPane.getChildren().add(emptyLabel);
            return;
        }
        for (ActivityCard activity : activities) {
            bidActivityFlowPane.getChildren().add(createActivityCard(activity));
        }
    }

    private void renderSellerCards(List<ActivityCard> activities, boolean hasAnyActivities) {
        sellerActivityFlowPane.getChildren().clear();
        if (activities.isEmpty()) {
            Label emptyLabel = new Label(hasAnyActivities
                    ? "No seller activity matches your current filters."
                    : "You have not created any auctions yet.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            sellerActivityFlowPane.getChildren().add(emptyLabel);
            return;
        }
        for (ActivityCard activity : activities) {
            sellerActivityFlowPane.getChildren().add(createActivityCard(activity));
        }
    }

    private VBox createActivityCard(ActivityCard activity) {
        VBox card = new VBox(8.0);
        card.setPrefSize(299.0, 272.0);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; "
                + "-fx-border-color: #d4e1ee; -fx-border-width: 1; -fx-border-radius: 12; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.12), 8, 0, 0, 0); "
                + "-fx-padding: 12;");

        Label title = new Label(activity.auctionTitle());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label role = new Label("Role: " + (SCOPE_BIDDING.equals(activity.scope()) ? "Bidder" : "Seller"));
        role.setStyle("-fx-text-fill: #2962ff; -fx-font-weight: bold;");

        Label status = new Label("Status: " + activity.status());
        status.setStyle("-fx-text-fill: " + statusColor(activity.status()) + "; -fx-font-weight: bold;");

        Label currentPrice = new Label("Current price: " + formatMoney(activity.currentPrice()));

        VBox content = new VBox(4.0, title, role, status, currentPrice);
        if (SCOPE_BIDDING.equals(activity.scope())) {
            content.getChildren().addAll(
                    new Label("Your max bid: " + formatMoney(activity.yourMaxBid())),
                    new Label("Minimum next bid: " + formatMoney(activity.minimumNextBid()))
            );
        } else {
            content.getChildren().addAll(
                    new Label("Starting price: " + formatMoney(activity.startingPrice())),
                    new Label("Bid count: " + activity.bidCount()),
                    new Label("Winner: " + safeWinnerName(activity.winnerBidderId()))
            );
        }
        content.getChildren().add(new Label(formatTimingLabel(activity.endTime(), activity.auctionStatus())));

        Button viewButton = new Button(buttonLabelForStatus(activity.scope(), activity.status()));
        viewButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; "
                + "-fx-background-radius: 8; -fx-font-weight: bold; -fx-cursor: hand;");
        viewButton.setOnAction(event -> {
            ClientApp.getInstance().setSelectedAuctionId(activity.auctionId());
            statusLabel.setText("Opening auction: " + activity.auctionTitle());
            SceneManager.switchScene("AuctionItem");
        });

        card.getChildren().addAll(content, viewButton);
        return card;
    }

    private Optional<ActivityCard> toBidActivity(AuctionDetailsResponseMessage response, String currentUsername) {
        if (response == null || currentUsername == null || currentUsername.isBlank()) {
            return Optional.empty();
        }

        List<BidView> bidHistory = response.getBidHistory() == null ? List.of() : response.getBidHistory();
        List<BidView> userBids = bidHistory.stream()
                .filter(Objects::nonNull)
                .filter(bid -> bid.getBidderId() != null && bid.getBidderId().equalsIgnoreCase(currentUsername))
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
                SCOPE_BIDDING,
                deriveBidStatus(response, currentUsername),
                response.getStatus(),
                yourMaxBid,
                response.getStartingPrice(),
                response.getCurrentPrice(),
                response.getMinimumNextBid(),
                bidHistory.size(),
                response.getWinnerBidderId(),
                response.getEndTime()
        ));
    }

    private Optional<ActivityCard> toSellerActivity(AuctionDetailsResponseMessage response, String currentUsername) {
        if (response == null || currentUsername == null || currentUsername.isBlank()) {
            return Optional.empty();
        }
        if (response.getSellerId() == null || !response.getSellerId().equalsIgnoreCase(currentUsername)) {
            return Optional.empty();
        }
        int bidCount = response.getBidHistory() == null ? 0 : response.getBidHistory().size();
        return Optional.of(new ActivityCard(
                response.getAuctionId(),
                response.getTitle(),
                SCOPE_SELLING,
                deriveSellerStatus(response),
                response.getStatus(),
                null,
                response.getStartingPrice(),
                response.getCurrentPrice(),
                response.getMinimumNextBid(),
                bidCount,
                response.getWinnerBidderId(),
                response.getEndTime()
        ));
    }

    private String resolveCurrentUsername() {
        if (AuthService.getInstance().getCurrentUsername() == null
                || AuthService.getInstance().getCurrentUsername().isBlank()) {
            return "";
        }
        return AuthService.getInstance().getCurrentUsername();
    }

    private String deriveBidStatus(AuctionDetailsResponseMessage response, String currentUsername) {
        AuctionStatus auctionStatus = response.getStatus();
        if (auctionStatus == AuctionStatus.CANCELED) {
            return STATUS_CANCELED;
        }
        if (isFinished(response) || auctionStatus == AuctionStatus.PAID) {
            return currentUsername.equalsIgnoreCase(response.getWinnerBidderId()) ? STATUS_WON : STATUS_LOST;
        }
        return currentUsername.equalsIgnoreCase(response.getLeadingBidderId()) ? STATUS_LEADING : STATUS_OUTBID;
    }

    private String deriveSellerStatus(AuctionDetailsResponseMessage response) {
        AuctionStatus auctionStatus = response.getStatus();
        if (auctionStatus == AuctionStatus.CANCELED) {
            return STATUS_CANCELED;
        }
        if (isOpen(response) || isRunning(response)) {
            return STATUS_ACTIVE;
        }
        if (isFinished(response) || auctionStatus == AuctionStatus.PAID) {
            if (response.getWinnerBidderId() != null && !response.getWinnerBidderId().isBlank()) {
                return STATUS_SOLD;
            }
            return STATUS_UNSOLD;
        }
        return STATUS_ACTIVE;
    }

    private int statusPriority(String status) {
        return switch (status) {
            case STATUS_ACTIVE -> 0;
            case STATUS_LEADING -> 1;
            case STATUS_OUTBID -> 2;
            case STATUS_SOLD -> 3;
            case STATUS_WON -> 4;
            case STATUS_UNSOLD -> 5;
            case STATUS_LOST -> 6;
            case STATUS_CANCELED -> 7;
            default -> 8;
        };
    }

    private String statusColor(String status) {
        return switch (status) {
            case STATUS_ACTIVE, STATUS_LEADING, STATUS_WON -> "#1f8f4c";
            case STATUS_SOLD -> "#2962ff";
            case STATUS_OUTBID, STATUS_UNSOLD, STATUS_LOST -> "#c13c21";
            case STATUS_CANCELED -> "#6b7280";
            default -> "#3f5569";
        };
    }

    private String buttonLabelForStatus(String scope, String status) {
        if (SCOPE_BIDDING.equals(scope) && STATUS_OUTBID.equals(status)) {
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
            return "No winner yet";
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

    private boolean isOpen(AuctionDetailsResponseMessage response) {
        return response != null
                && response.getStatus() == AuctionStatus.RUNNING
                && response.getStartTime() != null
                && LocalDateTime.now().isBefore(response.getStartTime());
    }

    private boolean isRunning(AuctionDetailsResponseMessage response) {
        LocalDateTime now = LocalDateTime.now();
        return response != null
                && response.getStatus() == AuctionStatus.RUNNING
                && response.getStartTime() != null
                && response.getEndTime() != null
                && !now.isBefore(response.getStartTime())
                && now.isBefore(response.getEndTime());
    }

    private boolean isFinished(AuctionDetailsResponseMessage response) {
        return response != null
                && response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime());
    }

    private void setActiveSection(ActivitySection section) {
        activeSection = section;
        boolean showBidPage = section == ActivitySection.BIDS;
        bidPageBox.setVisible(showBidPage);
        bidPageBox.setManaged(showBidPage);
        sellerPageBox.setVisible(!showBidPage);
        sellerPageBox.setManaged(!showBidPage);

        bidsNavLabel.setStyle(showBidPage
                ? "-fx-font-size: 15px; -fx-font-weight: bold; -fx-underline: true; -fx-text-fill: #153e5c; -fx-cursor: hand; -fx-padding: 6 10 6 10;"
                : "-fx-font-size: 15px; -fx-text-fill: #53677a; -fx-cursor: hand; -fx-padding: 6 10 6 10;");
        auctionsNavLabel.setStyle(showBidPage
                ? "-fx-font-size: 15px; -fx-text-fill: #53677a; -fx-cursor: hand; -fx-padding: 6 10 6 10;"
                : "-fx-font-size: 15px; -fx-font-weight: bold; -fx-underline: true; -fx-text-fill: #153e5c; -fx-cursor: hand; -fx-padding: 6 10 6 10;");
        updateSectionSummary();
    }

    private void updateSectionSummary() {
        if (!pendingAuctionIds.isEmpty()) {
            setNeutralStatus("Loading auction details...");
            return;
        }
        if (statusLabel.getStyle() != null && statusLabel.getStyle().contains("#d9534f")) {
            return;
        }
        hideStatus();
    }

    private void setNeutralStatus(String text) {
        setStatus(text, "-fx-text-fill: #3f5569; -fx-font-size: 12px;");
    }

    private void setErrorStatus(String text) {
        setStatus(text, "-fx-text-fill: #d9534f; -fx-font-size: 12px;");
    }

    private void hideStatus() {
        statusLabel.setText("");
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);
    }

    private void setStatus(String text, String style) {
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
            String scope,
            String status,
            AuctionStatus auctionStatus,
            BigDecimal yourMaxBid,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            int bidCount,
            String winnerBidderId,
            LocalDateTime endTime
    ) {
    }

    private enum ActivitySection {
        BIDS,
        AUCTIONS
    }
}
