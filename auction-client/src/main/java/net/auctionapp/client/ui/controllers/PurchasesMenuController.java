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
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.auction.AuctionStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
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
    private static final String STATUS_ALL = "All";
    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String STATUS_PAID = "PAID";

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private BorderPane rootPane;
    @FXML
    private VBox purchaseFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;
    @FXML
    private Label summaryLabel;

    private final List<PurchaseCard> allPurchases = new ArrayList<>();
    private final List<PurchaseCard> loadedPurchases = new ArrayList<>();
    private final Set<String> pendingAuctionIds = new HashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Purchases", true, "MainMenu.fxml");
        statusFilterComboBox.getItems().setAll(STATUS_ALL, STATUS_PENDING_PAYMENT, STATUS_PAID);
        statusFilterComboBox.getSelectionModel().selectFirst();
        summaryLabel.setManaged(false);
        summaryLabel.setVisible(false);
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                // No persistent request handlers to clean up.
            }
        });

        loadPurchases();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        cleanupHandlers();
        SceneManager.switchScene("MainMenu.fxml");
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadPurchases();
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

    private void cleanupHandlers() {
        // Deprecated: request/response now uses sendRequest with correlation IDs.
    }

    private void loadPurchases() {
        pendingAuctionIds.clear();
        allPurchases.clear();
        loadedPurchases.clear();
        renderPurchaseCards(allPurchases, false);
        showStatus("Loading your purchases...", STATUS_TEXT_STYLE);
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
        loadedPurchases.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                .map(summary -> summary == null ? null : summary.getAuctionId())
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (auctionIds.isEmpty()) {
            allPurchases.clear();
            renderPurchaseCards(allPurchases, false);
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

        toPurchaseCard(response, resolveCurrentUsername()).ifPresent(loadedPurchases::add);
        if (!pendingAuctionIds.isEmpty()) {
            showStatus("Loading auction details...", STATUS_TEXT_STYLE);
            return;
        }

        finalizeLoadedPurchases();
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
        finalizeLoadedPurchases();
    }

    private void finalizeLoadedPurchases() {
        allPurchases.clear();
        allPurchases.addAll(
                loadedPurchases.stream()
                        .sorted(Comparator.comparingInt(this::statusPriority)
                                .thenComparing(PurchaseCard::endTime, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(PurchaseCard::title, String.CASE_INSENSITIVE_ORDER))
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

        List<PurchaseCard> filtered = allPurchases.stream()
                .filter(purchase -> search.isBlank() || purchase.title().toLowerCase(Locale.ROOT).contains(search))
                .filter(purchase -> selectedStatus == null
                        || STATUS_ALL.equals(selectedStatus)
                        || purchase.purchaseStatus().equalsIgnoreCase(selectedStatus))
                .toList();

        renderPurchaseCards(filtered, !allPurchases.isEmpty());
        hideStatus();
    }

    private void renderPurchaseCards(List<PurchaseCard> purchases, boolean hasAnyPurchases) {
        purchaseFlowPane.getChildren().clear();
        if (purchases.isEmpty()) {
            Label emptyLabel = new Label(hasAnyPurchases
                    ? "No purchases match your current filters."
                    : "You do not have any purchases yet.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            purchaseFlowPane.getChildren().add(emptyLabel);
            return;
        }
        for (PurchaseCard purchase : purchases) {
            purchaseFlowPane.getChildren().add(loadPurchaseCard(purchase));
        }
    }

    private HBox loadPurchaseCard(PurchaseCard purchase) {
        String actionHint = STATUS_PENDING_PAYMENT.equalsIgnoreCase(purchase.purchaseStatus())
                ? "Action needed: complete payment."
                : "Payment completed.";
        AuctionCardController.CardData cardData = new AuctionCardController.CardData(
                purchase.imageUrl(),
                purchase.title(),
                "Order status: " + purchase.purchaseStatus(),
                statusColor(purchase.purchaseStatus()),
                "Seller: " + safeText(purchase.sellerId()),
                "Ended at: " + formatEndTime(purchase.endTime()),
                null,
                "Final Price",
                formatMoney(purchase.finalPrice()),
                "#0057ff",
                "Payment",
                actionHint,
                statusColor(purchase.purchaseStatus()),
                "",
                "",
                "#1f2933",
                "View auction",
                () -> {
            statusLabel.setText("Opening purchase: " + purchase.title());
            ClientApp.getInstance().setSelectedAuctionId(purchase.auctionId());
            SceneManager.switchScene("AuctionItemMenu.fxml");
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
            Label fallback = new Label("Failed to load purchase card.");
            fallback.setStyle("-fx-text-fill: #d9534f;");
            return new HBox(fallback);
        }
    }

    private Optional<PurchaseCard> toPurchaseCard(AuctionDetailsResponseMessage response, String currentUsername) {
        if (response == null || currentUsername == null || currentUsername.isBlank()) {
            return Optional.empty();
        }
        if (response.getWinnerBidderId() == null || !response.getWinnerBidderId().equalsIgnoreCase(currentUsername)) {
            return Optional.empty();
        }

        String purchaseStatus = derivePurchaseStatus(response);
        if (purchaseStatus == null) {
            return Optional.empty();
        }
        return Optional.of(new PurchaseCard(
                response.getAuctionId(),
                response.getTitle(),
                response.getSellerId(),
                response.getCurrentPrice(),
                purchaseStatus,
                response.getEndTime(),
                response.getImageUrl()
        ));
    }

    private String resolveCurrentUsername() {
        if (AuthService.getInstance().getCurrentUsername() == null || AuthService.getInstance().getCurrentUsername().isBlank()) {
            return "";
        }
        return AuthService.getInstance().getCurrentUsername();
    }

    private String derivePurchaseStatus(AuctionDetailsResponseMessage response) {
        if (response == null || response.getStatus() == null) {
            return null;
        }
        AuctionStatus status = response.getStatus();
        if (status == AuctionStatus.PAID) {
            return STATUS_PAID;
        }
        if (status == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime())) {
            return STATUS_PENDING_PAYMENT;
        }
        return null;
    }

    private int statusPriority(PurchaseCard card) {
        return STATUS_PENDING_PAYMENT.equals(card.purchaseStatus()) ? 0 : 1;
    }

    private String statusColor(String status) {
        return switch (status) {
            case STATUS_PENDING_PAYMENT -> "#c13c21";
            case STATUS_PAID -> "#1f8f4c";
            default -> "#3f5569";
        };
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }
        return "$" + amount.stripTrailingZeros().toPlainString();
    }

    private String formatEndTime(LocalDateTime endTime) {
        if (endTime == null) {
            return "N/A";
        }
        return CARD_TIME_FORMATTER.format(endTime);
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "N/A";
        }
        return value;
    }

    private record PurchaseCard(
            String auctionId,
            String title,
            String sellerId,
            BigDecimal finalPrice,
            String purchaseStatus,
            LocalDateTime endTime,
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
