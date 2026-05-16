package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
    private VBox bidFlowPane;
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
    public void handleRefresh(ActionEvent event) {
        loadMyBids();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadMyBids() {
        pendingAuctionIds.clear();
        allUserBids.clear();
        loadedUserBids.clear();
        renderBidCards(allUserBids);
        showStatus("Loading your bids...", STATUS_TEXT_STYLE);
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
        loadedUserBids.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                        .map(summary -> summary == null ? null : summary.getAuctionId())
                        .filter(id -> id != null && !id.isBlank())
                        .toList();

        if (auctionIds.isEmpty()) {
            allUserBids.clear();
            renderBidCards(allUserBids);
            showStatus("No auctions available yet.", STATUS_TEXT_STYLE);
            return;
        }

        pendingAuctionIds.addAll(auctionIds);
        showStatus("Loading auction details...", STATUS_TEXT_STYLE);
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

        toBidCard(response, resolveCurrentUsername()).ifPresent(loadedUserBids::add);
        if (!pendingAuctionIds.isEmpty()) {
            showStatus("Loading auction details...", STATUS_TEXT_STYLE);
            return;
        }

        finalizeLoadedBids();
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
        showStatus(errorMessage.getErrorMessage(), "-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
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
        hideStatus();
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
            bidFlowPane.getChildren().add(loadBidCard(bid));
        }
    }

    private HBox loadBidCard(BidCard bid) {
        AuctionListCardController.CardData cardData = new AuctionListCardController.CardData(
                bid.imageUrl(),
                bid.auctionTitle(),
                "Status: " + bid.status(),
                statusColor(bid.status()),
                "Minimum next bid: " + formatMoney(bid.minimumNextBid()),
                formatTimingLabel(bid.endTime(), bid.auctionStatus()),
                null,
                "Your Max Bid",
                formatMoney(bid.yourMaxBid()),
                "#0057ff",
                "Current Price",
                formatMoney(bid.currentPrice()),
                "#e03621",
                "",
                "",
                "#1f2933",
                buttonLabelForStatus(bid.status()),
                () -> {
            statusLabel.setText("Opening auction: " + bid.auctionTitle());
            ClientApp.getInstance().setSelectedAuctionId(bid.auctionId());
            SceneManager.switchScene("AuctionItem");
        },
                null,
                null
        );
        return loadAuctionCardComponent(cardData);
    }

    private HBox loadAuctionCardComponent(AuctionListCardController.CardData cardData) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/net/auctionapp/client/ui/fxml/AuctionCard.fxml"));
            HBox card = loader.load();
            AuctionListCardController controller = loader.getController();
            controller.bindCard(cardData);
            return card;
        } catch (IOException | RuntimeException e) {
            Label fallback = new Label("Failed to load bid card.");
            fallback.setStyle("-fx-text-fill: #d9534f;");
            return new HBox(fallback);
        }
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
                response.getStatus(),
                response.getImageUrl()
        ));
    }

    private String resolveCurrentUsername() {
        if (AuthService.getInstance().getCurrentUsername() == null || AuthService.getInstance().getCurrentUsername().isBlank()) {
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

    private boolean isFinished(AuctionDetailsResponseMessage response) {
        return response != null
                && response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime());
    }

    private record BidCard(
            String auctionId,
            String auctionTitle,
            BigDecimal yourMaxBid,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            LocalDateTime endTime,
            String status,
            AuctionStatus auctionStatus,
            String imageUrl
    ) {
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
}
