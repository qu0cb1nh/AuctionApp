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
import net.auctionapp.common.messages.types.BidView;
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
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

public class MyBidsController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String STATUS_TEXT_STYLE = "-fx-text-fill: #666666;";
    private static final String STATUS_ALL = "All";
    private static final String STATUS_LEADING = "LEADING";
    private static final String STATUS_OUTBID = "OUTBID";
    private static final String STATUS_WON = "WON";
    private static final String STATUS_LOST = "LOST";
    private static final String STATUS_CANCELED = "CANCELED";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private BorderPane rootPane;
    @FXML
    private FlowPane bidFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;

    private final List<BidCard> allUserBids = new ArrayList<>();
    private final List<BidCard> loadedUserBids = new ArrayList<>();
    private final Set<String> pendingAuctionIds = new HashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("My Bids", true, "MainMenu");

        statusFilterComboBox.getItems().setAll(
                STATUS_ALL,
                STATUS_LEADING,
                STATUS_OUTBID,
                STATUS_WON,
                STATUS_LOST,
                STATUS_CANCELED
        );
        statusFilterComboBox.getSelectionModel().selectFirst();
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                // No persistent request handlers to clean up.
            }
        });

        loadMyBids();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        cleanupHandlers();
        SceneNavigator.switchScene("MainMenu");
    }

    @FXML
    public void handleSignOut(ActionEvent event) {
        cleanupHandlers();
        if (ClientApp.getInstance() != null) {
            ClientApp.getInstance().setCurrentUser(null, null);
        }
        SceneNavigator.switchScene("LoginMenu");
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadMyBids();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void cleanupHandlers() {
        // Deprecated: request/response now uses sendRequest with correlation IDs.
    }

    private void loadMyBids() {
        pendingAuctionIds.clear();
        allUserBids.clear();
        loadedUserBids.clear();
        renderBidCards(allUserBids);
        statusLabel.setStyle(STATUS_TEXT_STYLE);
        statusLabel.setText("Loading your bids...");
        ClientApp.getInstance().sendRequest(new GetAuctionListRequestMessage(), this::handleAuctionListRequestResult);
    }

    private void handleAuctionListRequestResult(Message message, Throwable throwable) {
        if (throwable != null) {
            statusLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
            statusLabel.setText("Failed to load bids: " + throwable.getMessage());
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
        loadedUserBids.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                        .map(summary -> summary == null ? null : summary.getAuctionId())
                        .filter(id -> id != null && !id.isBlank())
                        .toList();

        if (auctionIds.isEmpty()) {
            allUserBids.clear();
            renderBidCards(allUserBids);
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

        toBidCard(response, resolveCurrentUsername()).ifPresent(loadedUserBids::add);
        if (!pendingAuctionIds.isEmpty()) {
            statusLabel.setText("Loading details for " + pendingAuctionIds.size() + " auction(s)...");
            return;
        }

        finalizeLoadedBids();
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
        finalizeLoadedBids();
    }

    private void finalizeLoadedBids() {
        allUserBids.clear();
        allUserBids.addAll(
                loadedUserBids.stream()
                        .sorted(Comparator.comparingInt(this::statusPriority)
                                .thenComparing(BidCard::endTime, Comparator.nullsLast(LocalDateTime::compareTo))
                                .thenComparing(BidCard::auctionTitle, String.CASE_INSENSITIVE_ORDER))
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

        List<BidCard> filtered = allUserBids.stream()
                .filter(bid -> search.isBlank() || bid.auctionTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(bid -> selectedStatus == null
                        || STATUS_ALL.equals(selectedStatus)
                        || bid.status().equalsIgnoreCase(selectedStatus))
                .toList();

        renderBidCards(filtered);
        statusLabel.setStyle(STATUS_TEXT_STYLE);
        statusLabel.setText("Showing " + filtered.size() + " of " + allUserBids.size() + " auctions.");
    }

    private void renderBidCards(List<BidCard> bids) {
        bidFlowPane.getChildren().clear();
        if (bids.isEmpty()) {
            Label emptyLabel = new Label("You have not placed bids on any auction yet.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            bidFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (BidCard bid : bids) {
            bidFlowPane.getChildren().add(createBidCard(bid));
        }
    }

    private VBox createBidCard(BidCard bid) {
        VBox card = new VBox(8.0);
        card.setPrefSize(240.0, 230.0);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0); "
                + "-fx-padding: 10;");

        Label title = new Label(bid.auctionTitle());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label yourBid = new Label("Your max bid: " + formatMoney(bid.yourMaxBid()));
        Label highestBid = new Label("Current price: " + formatMoney(bid.currentPrice()));
        Label nextBid = new Label("Minimum next bid: " + formatMoney(bid.minimumNextBid()));
        Label endTime = new Label(formatTimingLabel(bid.endTime(), bid.auctionStatus()));
        Label status = new Label("Status: " + bid.status());
        status.setStyle("-fx-text-fill: " + statusColor(bid.status()) + "; -fx-font-weight: bold;");

        Button viewButton = new Button(buttonLabelForStatus(bid.status()));
        viewButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        viewButton.setOnAction(event -> {
            statusLabel.setText("Opening auction: " + bid.auctionTitle());
            ClientApp.getInstance().setSelectedAuctionId(bid.auctionId());
            SceneNavigator.switchScene("AuctionItem");
        });

        card.getChildren().addAll(title, yourBid, highestBid, nextBid, endTime, status, viewButton);
        return card;
    }

    private java.util.Optional<BidCard> toBidCard(AuctionDetailsResponseMessage response, String currentUsername) {
        if (response == null || currentUsername == null || currentUsername.isBlank()) {
            return java.util.Optional.empty();
        }
        List<BidView> bidHistory = response.getBidHistory() == null ? List.of() : response.getBidHistory();
        List<BidView> userBids = bidHistory.stream()
                .filter(Objects::nonNull)
                .filter(bid -> bid.getBidderId() != null && bid.getBidderId().equalsIgnoreCase(currentUsername))
                .filter(bid -> bid.getAmount() != null)
                .toList();
        if (userBids.isEmpty()) {
            return java.util.Optional.empty();
        }

        BigDecimal yourMaxBid = userBids.stream()
                .map(BidView::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        String status = deriveBidStatus(response, currentUsername);
        return java.util.Optional.of(new BidCard(
                response.getAuctionId(),
                response.getTitle(),
                yourMaxBid,
                response.getCurrentPrice(),
                response.getMinimumNextBid(),
                response.getEndTime(),
                status,
                response.getStatus()
        ));
    }

    private String resolveCurrentUsername() {
        if (ClientApp.getInstance() == null || ClientApp.getInstance().getCurrentUsername() == null
                || ClientApp.getInstance().getCurrentUsername().isBlank()) {
            return "";
        }
        return ClientApp.getInstance().getCurrentUsername();
    }

    private String deriveBidStatus(AuctionDetailsResponseMessage response, String currentUsername) {
        AuctionStatus auctionStatus = response.getStatus();
        if (auctionStatus == AuctionStatus.CANCELED) {
            return STATUS_CANCELED;
        }
        if (auctionStatus == AuctionStatus.FINISHED || auctionStatus == AuctionStatus.PAID) {
            return currentUsername.equalsIgnoreCase(response.getWinnerBidderId()) ? STATUS_WON : STATUS_LOST;
        }
        return currentUsername.equalsIgnoreCase(response.getLeadingBidderId()) ? STATUS_LEADING : STATUS_OUTBID;
    }

    private int statusPriority(BidCard bidCard) {
        return switch (bidCard.status()) {
            case STATUS_LEADING -> 0;
            case STATUS_OUTBID -> 1;
            case STATUS_WON -> 2;
            case STATUS_LOST -> 3;
            case STATUS_CANCELED -> 4;
            default -> 5;
        };
    }

    private String statusColor(String status) {
        return switch (status) {
            case STATUS_LEADING, STATUS_WON -> "#1f8f4c";
            case STATUS_OUTBID, STATUS_LOST -> "#c13c21";
            case STATUS_CANCELED -> "#6b7280";
            default -> "#3f5569";
        };
    }

    private String buttonLabelForStatus(String status) {
        return switch (status) {
            case STATUS_OUTBID -> "Bid again";
            default -> "View auction";
        };
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }
        return "$" + amount.stripTrailingZeros().toPlainString();
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

    private record BidCard(
            String auctionId,
            String auctionTitle,
            BigDecimal yourMaxBid,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            LocalDateTime endTime,
            String status,
            AuctionStatus auctionStatus
    ) {
    }
}
