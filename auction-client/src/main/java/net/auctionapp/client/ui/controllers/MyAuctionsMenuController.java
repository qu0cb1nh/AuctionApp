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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.utils.DurationFormatUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

public class MyAuctionsMenuController implements Initializable {
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
    private VBox auctionFlowPane;
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
        appHeaderController.setupHeader("My Auctions", true, "MainMenu.fxml");

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
        showStatus("Loading your auctions...", STATUS_TEXT_STYLE);
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
        loadedUserAuctions.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                        .map(summary -> summary == null ? null : summary.getAuctionId())
                        .filter(id -> id != null && !id.isBlank())
                        .toList();

        if (auctionIds.isEmpty()) {
            allUserAuctions.clear();
            renderAuctionCards(allUserAuctions);
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

        toAuctionCard(response, resolveCurrentUsername()).ifPresent(loadedUserAuctions::add);
        if (!pendingAuctionIds.isEmpty()) {
            showStatus("Loading auction details...", STATUS_TEXT_STYLE);
            return;
        }

        finalizeLoadedAuctions();
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
        showStatus(errorMessage.getErrorMessage(), "-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
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
        hideStatus();
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
            auctionFlowPane.getChildren().add(loadAuctionCard(auction));
        }
    }

    private HBox loadAuctionCard(AuctionCard auction) {
        String winner = auction.winnerBidderId() == null || auction.winnerBidderId().isBlank()
                ? "No winner yet"
                : auction.winnerBidderId();
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                auction.imageUrl(),
                auction.title(),
                "Seller status: " + auction.sellerStatus(),
                statusColor(auction.sellerStatus()),
                "Auction state: " + auction.auctionStatus().name(),
                "Starting price: " + formatMoney(auction.startingPrice()),
                formatTimingLabel(auction.endTime(), auction.auctionStatus()),
                "Current Price",
                formatMoney(auction.currentPrice()),
                "#0057ff",
                "Bids",
                String.valueOf(auction.bidCount()),
                "#1f2933",
                "Winner",
                winner,
                "#1f2933",
                "View auction",
                () -> {
            statusLabel.setText("Opening auction: " + auction.title());
            ClientApp.getInstance().setSelectedAuctionId(auction.auctionId());
            SceneManager.switchScene("AuctionItemMenu.fxml");
        },
                "Manage auction",
                () -> handleManageAuction(auction.auctionId())
        );
        return loadAuctionCardComponent(cardData);
    }

    private void handleManageAuction(String auctionId) {
        ClientApp.getInstance().setSelectedAuctionId(auctionId);
        SceneManager.switchScene("ManageAuctionMenu.fxml");
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
                response.getWinnerBidderId(),
                response.getImageUrl()
        ));
    }

    private String resolveCurrentUsername() {
        if (AuthService.getInstance().getCurrentUsername() == null || AuthService.getInstance().getCurrentUsername().isBlank()) {
            return "";
        }
        return AuthService.getInstance().getCurrentUsername();
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
                && (response.getStatus() == AuctionStatus.PAID || response.getStatus() == AuctionStatus.CANCELED
                || (response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime())));
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
            String winnerBidderId,
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
