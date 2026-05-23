package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.AuctionCardController;

import net.auctionapp.client.ui.controllers.components.HeaderController;

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
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.utils.DurationFormatUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.AuctionSummary;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.auction.AuctionStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class AuctionListMenuController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
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
    private boolean adminUser;

    @FXML
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Explore Auctions");
        adminUser = ClientSession.getInstance().isAdmin();
        statusFilterComboBox.getItems().setAll(
                STATUS_ALL,
                "RUNNING",
                "PAID",
                "CANCELED"
        );
        statusFilterComboBox.getSelectionModel().select("RUNNING");
        sortComboBox.getItems().setAll(SORT_ENDING_SOON, SORT_HIGHEST_BID, SORT_NEWEST_START);
        sortComboBox.getSelectionModel().select(SORT_ENDING_SOON);

        requestAuctionList();
    }

    private void requestAuctionList() {
        showListStatus("Loading auctions...", "-fx-text-fill: #666666;");
        AuctionService.getInstance().requestAuctionList(this::handleAuctionListResult);
    }

    private void handleAuctionListResult(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            showListStatus("Unexpected response from server.", "-fx-text-fill: #d9534f;");
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

    private void handleErrorResponse(ErrorMessage errorMessage) {
        showListStatus(errorMessage.getErrorMessage(), "-fx-text-fill: #d9534f;");
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
        showListStatus(null, "-fx-text-fill: #666666;");
        if (allAuctions.isEmpty()) {
            showListStatus("No auctions available.", "-fx-text-fill: #666666;");
            return;
        }
        if (filtered.isEmpty()) {
            showListStatus("No auctions match your search/filter.", "-fx-text-fill: #666666;");
            return;
        }
        hideListStatus();
    }

    private void renderAuctionCards(List<AuctionSummary> auctions) {
        auctionFlowPane.getChildren().clear();
        if (auctions.isEmpty()) {
            Label emptyLabel = new Label("No auctions found.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            auctionFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (AuctionSummary auction : auctions) {
            auctionFlowPane.getChildren().add(loadAuctionCard(auction));
        }
    }

    private HBox loadAuctionCard(AuctionSummary auction) {
        String displayStatus = deriveDisplayStatus(auction);
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                auction.getImageUrl(),
                auction.getItemType(),
                auction.getTitle(),
                "Status: " + displayStatus,
                statusColor(displayStatus),
                "Minimum Next Bid: " + formatPrice(auction.getMinimumNextBid()),
                "Start: " + formatDateTime(auction.getStartTime()),
                "End: " + formatDateTime(auction.getEndTime()),
                "Current Bid",
                formatPrice(auction.getCurrentPrice()),
                "#0057ff",
                "Time Left",
                formatTimingLabel(auction).replace("Ends in: ", "").replace("Starts in: ", ""),
                "#e03621",
                "Top Bidder",
                formatTopBidder(auction.getLeadingBidderId()),
                "#1f2933",
                resolveButtonLabel(displayStatus),
                () -> handleViewItem(auction.getAuctionId()),
                adminUser ? "Manage auction" : null,
                adminUser ? () -> handleManageAuction(auction.getAuctionId()) : null
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
            fallback.setStyle("-fx-text-fill: #d9534f;");
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

    private String formatTimingLabel(AuctionSummary auction) {
        if (auction == null || auction.getStatus() == null || auction.getEndTime() == null) {
            return "Time: N/A";
        }
        LocalDateTime now = LocalDateTime.now();
        if (auction.getStartTime() != null && now.isBefore(auction.getStartTime())) {
            return "Starts in: " + DurationFormatUtil.formatRemainingDuration(Duration.between(now, auction.getStartTime()));
        }
        if (auction.getStatus() == AuctionStatus.RUNNING && !now.isBefore(auction.getStartTime()) && now.isBefore(auction.getEndTime())) {
            return "Ends in: " + DurationFormatUtil.formatRemainingDuration(Duration.between(now, auction.getEndTime()));
        }
        if (now.isAfter(auction.getEndTime())) {
            return "Ended";
        }
        return "Scheduled";
    }

    private String statusColor(String status) {
        return switch (status) {
            case "RUNNING" -> "#1f8f4c";
            case "PAID" -> "#2e7d32";
            case "CANCELED" -> "#6b7280";
            default -> "#6b7280";
        };
    }

    private String resolveButtonLabel(String status) {
        if ("RUNNING".equalsIgnoreCase(status)) {
            return "Bid now!";
        }
        return "View details";
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

    private void hideListStatus() {
        listStatusLabel.setText("");
        listStatusLabel.setManaged(false);
        listStatusLabel.setVisible(false);
    }

    private void showListStatus(String text, String style) {
        if (text == null || text.isBlank()) {
            hideListStatus();
            return;
        }
        listStatusLabel.setManaged(true);
        listStatusLabel.setVisible(true);
        listStatusLabel.setStyle(style);
        listStatusLabel.setText(text);
    }

}
