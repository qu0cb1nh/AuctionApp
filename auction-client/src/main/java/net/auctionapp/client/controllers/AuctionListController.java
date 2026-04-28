package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.client.utils.DurationFormatUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.AuctionSummary;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
import net.auctionapp.common.models.auction.AuctionStatus;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class AuctionListController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String STATUS_ALL = "ALL";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private BorderPane rootPane;
    @FXML
    private FlowPane auctionFlowPane;
    @FXML
    private Label listStatusLabel;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;

    private final List<AuctionSummary> allAuctions = new ArrayList<>();

    @FXML
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Explore Auctions", true, "MainMenu");
        statusFilterComboBox.getItems().setAll(
                STATUS_ALL,
                "OPEN",
                "RUNNING",
                "FINISHED",
                "PAID",
                "CANCELED"
        );
        statusFilterComboBox.getSelectionModel().select("RUNNING");
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                // No persistent request handlers to clean up.
            }
        });

        requestAuctionList();
    }

    private void requestAuctionList() {
        listStatusLabel.setStyle("-fx-text-fill: #666666;");
        listStatusLabel.setText("Loading auctions...");
        ClientApp.getInstance().sendRequest(new GetAuctionListRequestMessage(), this::handleAuctionListResult);
    }

    private void handleAuctionListResult(Message message, Throwable throwable) {
        if (throwable != null) {
            listStatusLabel.setStyle("-fx-text-fill: #d9534f;");
            listStatusLabel.setText("Failed to load auctions: " + throwable.getMessage());
            return;
        }
        if (message instanceof ErrorMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            listStatusLabel.setStyle("-fx-text-fill: #d9534f;");
            listStatusLabel.setText("Unexpected response from server.");
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
        listStatusLabel.setStyle("-fx-text-fill: #d9534f;");
        listStatusLabel.setText(errorMessage.getErrorMessage());
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

        List<AuctionSummary> filtered = allAuctions.stream()
                .filter(auction -> search.isBlank() || auction.getTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(auction -> selectedStatus == null
                        || STATUS_ALL.equalsIgnoreCase(selectedStatus)
                        || auction.getStatus().name().equalsIgnoreCase(selectedStatus))
                .toList();

        renderAuctionCards(filtered);
        listStatusLabel.setStyle("-fx-text-fill: #666666;");
        if (allAuctions.isEmpty()) {
            listStatusLabel.setText("No auctions available.");
            return;
        }
        if (filtered.isEmpty()) {
            listStatusLabel.setText("No auctions match your search/filter.");
            return;
        }
        listStatusLabel.setText("Showing " + filtered.size() + " of " + allAuctions.size() + " auction(s).");
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
            auctionFlowPane.getChildren().add(createAuctionCard(auction));
        }
    }

    private VBox createAuctionCard(AuctionSummary auction) {
        Label statusLabel = new Label(auction.getStatus() == null ? "Status: N/A" : "Status: " + auction.getStatus().name());
        statusLabel.setStyle("-fx-text-fill: " + statusColor(auction.getStatus()) + "; -fx-font-weight: bold;");

        Label titleLabel = new Label(auction.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label priceLabel = new Label("Current Bid: " + formatPrice(auction.getCurrentPrice()));
        priceLabel.setStyle("-fx-text-fill: #c13c21;");

        Label nextBidLabel = new Label("Minimum Next Bid: " + formatPrice(auction.getMinimumNextBid()));
        Label topBidderLabel = new Label("Top bidder: " + formatTopBidder(auction.getLeadingBidderId()));
        Label startLabel = new Label("Start: " + formatDateTime(auction.getStartTime()));
        Label endLabel = new Label("End: " + formatDateTime(auction.getEndTime()));
        Label timingLabel = new Label(formatTimingLabel(auction));
        timingLabel.setStyle("-fx-text-fill: #3f5569;");

        Button bidButton = new Button(resolveButtonLabel(auction.getStatus()));
        bidButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        bidButton.setOnAction(event -> handleViewItem(auction.getAuctionId()));

        VBox card = new VBox(
                8.0,
                statusLabel,
                titleLabel,
                priceLabel,
                nextBidLabel,
                topBidderLabel,
                startLabel,
                endLabel,
                timingLabel,
                bidButton
        );
        card.setPadding(new Insets(10.0));
        card.setPrefWidth(206.0);
        card.setPrefHeight(245.0);
        card.setStyle(
                "-fx-background-color: white; "
                        + "-fx-background-radius: 10; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0);"
        );
        return card;
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
        if (auction.getStatus() == AuctionStatus.OPEN && auction.getStartTime() != null && now.isBefore(auction.getStartTime())) {
            return "Starts in: " + DurationFormatUtil.formatRemainingDuration(Duration.between(now, auction.getStartTime()));
        }
        if (auction.getStatus() == AuctionStatus.RUNNING && now.isBefore(auction.getEndTime())) {
            return "Ends in: " + DurationFormatUtil.formatRemainingDuration(Duration.between(now, auction.getEndTime()));
        }
        if (now.isAfter(auction.getEndTime())) {
            return "Ended";
        }
        return "Scheduled";
    }

    private String statusColor(AuctionStatus status) {
        if (status == null) {
            return "#6b7280";
        }
        return switch (status) {
            case RUNNING -> "#1f8f4c";
            case OPEN -> "#2962ff";
            case FINISHED -> "#c13c21";
            case PAID -> "#2e7d32";
            case CANCELED -> "#6b7280";
        };
    }

    private String resolveButtonLabel(AuctionStatus status) {
        if (status == AuctionStatus.RUNNING) {
            return "Bid now!";
        }
        return "View details";
    }

    private void handleViewItem(String auctionId) {
        ClientApp.getInstance().setSelectedAuctionId(auctionId);
        SceneNavigator.switchScene("AuctionItem");
    }

}
