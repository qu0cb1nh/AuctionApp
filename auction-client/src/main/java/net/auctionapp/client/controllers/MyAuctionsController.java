package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
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
import java.util.ResourceBundle;
import java.util.Set;

public class MyAuctionsController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String STATUS_TEXT_STYLE = "-fx-text-fill: #666666;";
    private static final String STATUS_ALL = "All";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SOLD = "SOLD";
    private static final String STATUS_UNSOLD = "UNSOLD";
    private static final String STATUS_CANCELED = "CANCELED";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private BorderPane rootPane;
    @FXML
    private FlowPane auctionFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;

    private final List<AuctionCard> allUserAuctions = new ArrayList<>();
    private final List<AuctionCard> loadedUserAuctions = new ArrayList<>();
    private final Set<String> pendingAuctionIds = new HashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("My Auctions", true, "MainMenu");

        statusFilterComboBox.getItems().setAll(
                STATUS_ALL,
                STATUS_ACTIVE,
                STATUS_SOLD,
                STATUS_UNSOLD,
                STATUS_CANCELED
        );
        statusFilterComboBox.getSelectionModel().selectFirst();
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                // No persistent request handlers to clean up.
            }
        });

        loadMyAuctions();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadMyAuctions();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadMyAuctions() {
        pendingAuctionIds.clear();
        allUserAuctions.clear();
        loadedUserAuctions.clear();
        renderAuctionCards(allUserAuctions);
        statusLabel.setStyle(STATUS_TEXT_STYLE);
        statusLabel.setText("Loading your auctions...");
        ClientApp.getInstance().sendRequest(new GetAuctionListRequestMessage(), this::handleAuctionListRequestResult);
    }

    private void handleAuctionListRequestResult(Message message, Throwable throwable) {
        if (throwable != null) {
            statusLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
            statusLabel.setText("Failed to load auctions: " + throwable.getMessage());
            return;
        }
        if (message instanceof ErrorMessage errorMessage) {
            handleErrorResponse(errorMessage);
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            statusLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
            statusLabel.setText("Unexpected response from server.");
            return;
        }
        handleAuctionListResponse(response);
    }

    private void handleAuctionListResponse(AuctionListResponseMessage response) {
        pendingAuctionIds.clear();
        loadedUserAuctions.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                        .map(summary -> summary == null ? null : summary.getAuctionId())
                        .filter(id -> id != null && !id.isBlank())
                        .toList();

        if (auctionIds.isEmpty()) {
            allUserAuctions.clear();
            renderAuctionCards(allUserAuctions);
            statusLabel.setText("No auctions available yet.");
            return;
        }

        pendingAuctionIds.addAll(auctionIds);
        statusLabel.setText("Loading details for " + pendingAuctionIds.size() + " auction(s)...");
        for (String auctionId : auctionIds) {
            ClientApp.getInstance().sendRequest(
                    new GetAuctionDetailsRequestMessage(auctionId),
                    (detailResponse, throwable) -> handleAuctionDetailsRequestResult(auctionId, detailResponse, throwable)
            );
        }
    }

    private void handleAuctionDetailsRequestResult(String auctionId, Message message, Throwable throwable) {
        if (throwable != null) {
            completeLoadingAfterDetailFailure(auctionId, null);
            return;
        }
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

        toAuctionCard(response, resolveCurrentUsername()).ifPresent(loadedUserAuctions::add);
        if (!pendingAuctionIds.isEmpty()) {
            statusLabel.setText("Loading details for " + pendingAuctionIds.size() + " auction(s)...");
            return;
        }

        finalizeLoadedAuctions();
    }

    private void completeLoadingAfterDetailFailure(String auctionId, String errorMessage) {
        if (auctionId != null) {
            pendingAuctionIds.remove(auctionId);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            statusLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
            statusLabel.setText(errorMessage);
        }
        if (!pendingAuctionIds.isEmpty()) {
            statusLabel.setText("Loading details for " + pendingAuctionIds.size() + " auction(s)...");
            return;
        }
        finalizeLoadedAuctions();
    }

    private void finalizeLoadedAuctions() {
        allUserAuctions.clear();
        allUserAuctions.addAll(
                loadedUserAuctions.stream()
                        .sorted(Comparator.comparingInt(this::statusPriority)
                                .thenComparing(AuctionCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(AuctionCard::title, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );
        applyFilters();
    }

    private void handleErrorResponse(ErrorMessage errorMessage) {
        statusLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
        statusLabel.setText(errorMessage.getErrorMessage());
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();

        List<AuctionCard> filtered = allUserAuctions.stream()
                .filter(auction -> search.isBlank() || auction.title().toLowerCase(Locale.ROOT).contains(search))
                .filter(auction -> selectedStatus == null
                        || STATUS_ALL.equals(selectedStatus)
                        || auction.sellerStatus().equalsIgnoreCase(selectedStatus))
                .toList();

        renderAuctionCards(filtered);
        statusLabel.setStyle(STATUS_TEXT_STYLE);
        statusLabel.setText("Showing " + filtered.size() + " of " + allUserAuctions.size() + " auctions.");
    }

    private void renderAuctionCards(List<AuctionCard> auctions) {
        auctionFlowPane.getChildren().clear();
        if (auctions.isEmpty()) {
            Label emptyLabel = new Label("You have not created any auctions yet.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            auctionFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (AuctionCard auction : auctions) {
            auctionFlowPane.getChildren().add(createAuctionCard(auction));
        }
    }

    private VBox createAuctionCard(AuctionCard auction) {
        VBox card = new VBox(8.0);
        card.setPrefSize(240.0, 240.0);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0); "
                + "-fx-padding: 10;");

        Label title = new Label(auction.title());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label status = new Label("Seller status: " + auction.sellerStatus());
        status.setStyle("-fx-text-fill: " + statusColor(auction.sellerStatus()) + "; -fx-font-weight: bold;");

        Label auctionState = new Label("Auction state: " + auction.auctionStatus().name());
        Label currentPrice = new Label("Current price: " + formatMoney(auction.currentPrice()));
        Label startingPrice = new Label("Starting price: " + formatMoney(auction.startingPrice()));
        Label bidCount = new Label("Bid count: " + auction.bidCount());
        Label timing = new Label(formatTimingLabel(auction.endTime(), auction.auctionStatus()));

        String winner = auction.winnerBidderId() == null || auction.winnerBidderId().isBlank()
                ? "No winner yet"
                : auction.winnerBidderId();
        Label winnerLabel = new Label("Winner: " + winner);

        Button viewButton = new Button("View auction");
        viewButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        viewButton.setOnAction(event -> {
            statusLabel.setText("Opening auction: " + auction.title());
            ClientApp.getInstance().setSelectedAuctionId(auction.auctionId());
            SceneNavigator.switchScene("AuctionItem");
        });

        card.getChildren().addAll(
                title,
                status,
                auctionState,
                currentPrice,
                startingPrice,
                bidCount,
                timing,
                winnerLabel,
                viewButton
        );
        return card;
    }

    private java.util.Optional<AuctionCard> toAuctionCard(AuctionDetailsResponseMessage response, String currentUsername) {
        if (response == null || currentUsername == null || currentUsername.isBlank()) {
            return java.util.Optional.empty();
        }
        if (response.getSellerId() == null || !response.getSellerId().equalsIgnoreCase(currentUsername)) {
            return java.util.Optional.empty();
        }

        int bidCount = response.getBidHistory() == null ? 0 : response.getBidHistory().size();
        return java.util.Optional.of(new AuctionCard(
                response.getAuctionId(),
                response.getTitle(),
                deriveSellerStatus(response),
                response.getStatus(),
                response.getStartingPrice(),
                response.getCurrentPrice(),
                bidCount,
                response.getEndTime(),
                response.getWinnerBidderId()
        ));
    }

    private String resolveCurrentUsername() {
        if (ClientApp.getInstance() == null || ClientApp.getInstance().getCurrentUsername() == null
                || ClientApp.getInstance().getCurrentUsername().isBlank()) {
            return "";
        }
        return ClientApp.getInstance().getCurrentUsername();
    }

    private String deriveSellerStatus(AuctionDetailsResponseMessage response) {
        AuctionStatus auctionStatus = response.getStatus();
        if (auctionStatus == AuctionStatus.CANCELED) {
            return STATUS_CANCELED;
        }
        if (auctionStatus == AuctionStatus.OPEN || auctionStatus == AuctionStatus.RUNNING) {
            return STATUS_ACTIVE;
        }
        if (auctionStatus == AuctionStatus.FINISHED || auctionStatus == AuctionStatus.PAID) {
            if (response.getWinnerBidderId() != null && !response.getWinnerBidderId().isBlank()) {
                return STATUS_SOLD;
            }
            return STATUS_UNSOLD;
        }
        return STATUS_ACTIVE;
    }

    private int statusPriority(AuctionCard card) {
        return switch (card.sellerStatus()) {
            case STATUS_ACTIVE -> 0;
            case STATUS_SOLD -> 1;
            case STATUS_UNSOLD -> 2;
            case STATUS_CANCELED -> 3;
            default -> 4;
        };
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }
        return "$" + amount.stripTrailingZeros().toPlainString();
    }

    private String statusColor(String status) {
        return switch (status) {
            case STATUS_ACTIVE -> "#1f8f4c";
            case STATUS_SOLD -> "#2962ff";
            case STATUS_UNSOLD -> "#c13c21";
            case STATUS_CANCELED -> "#6b7280";
            default -> "#3f5569";
        };
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
        if (auctionStatus == AuctionStatus.OPEN) {
            return "Starts before: " + CARD_TIME_FORMATTER.format(endTime);
        }
        return "Ended at: " + CARD_TIME_FORMATTER.format(endTime);
    }

    private record AuctionCard(
            String auctionId,
            String title,
            String sellerStatus,
            AuctionStatus auctionStatus,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            int bidCount,
            LocalDateTime endTime,
            String winnerBidderId
    ) {
    }
}
