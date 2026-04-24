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
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
import net.auctionapp.common.models.auction.AuctionStatus;

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
import java.util.function.Consumer;

public class PurchasesController implements Initializable {
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
    private FlowPane purchaseFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;

    private final List<PurchaseCard> allPurchases = new ArrayList<>();
    private final List<PurchaseCard> loadedPurchases = new ArrayList<>();
    private final Set<String> pendingAuctionIds = new HashSet<>();

    private Consumer<Message> auctionListHandler;
    private Consumer<Message> auctionDetailsHandler;
    private Consumer<Message> errorHandler;
    private boolean handlersRegistered;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Purchases", true, "MainMenu");
        statusFilterComboBox.getItems().setAll(STATUS_ALL, STATUS_PENDING_PAYMENT, STATUS_PAID);
        statusFilterComboBox.getSelectionModel().selectFirst();
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                cleanupHandlers();
            }
        });

        registerMessageHandlers();
        loadPurchases();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        cleanupHandlers();
        SceneNavigator.switchScene("MainMenu");
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadPurchases();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void registerMessageHandlers() {
        if (handlersRegistered) {
            return;
        }
        auctionListHandler = this::handleAuctionListResponse;
        auctionDetailsHandler = this::handleAuctionDetailsResponse;
        errorHandler = this::handleErrorResponse;
        ClientApp.getInstance().addMessageHandler(MessageType.AUCTION_LIST_RESPONSE, auctionListHandler);
        ClientApp.getInstance().addMessageHandler(MessageType.AUCTION_DETAILS_RESPONSE, auctionDetailsHandler);
        ClientApp.getInstance().addMessageHandler(MessageType.ERROR, errorHandler);
        handlersRegistered = true;
    }

    private void cleanupHandlers() {
        if (!handlersRegistered) {
            return;
        }
        if (auctionListHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.AUCTION_LIST_RESPONSE, auctionListHandler);
        }
        if (auctionDetailsHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.AUCTION_DETAILS_RESPONSE, auctionDetailsHandler);
        }
        if (errorHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.ERROR, errorHandler);
        }
        handlersRegistered = false;
    }

    private void loadPurchases() {
        pendingAuctionIds.clear();
        allPurchases.clear();
        loadedPurchases.clear();
        renderPurchaseCards(allPurchases);
        statusLabel.setStyle(STATUS_TEXT_STYLE);
        statusLabel.setText("Loading your purchases...");
        ClientApp.getInstance().getNetworkService().sendMessage(new GetAuctionListRequestMessage());
    }

    private void handleAuctionListResponse(Message message) {
        if (!(message instanceof AuctionListResponseMessage response)) {
            return;
        }
        pendingAuctionIds.clear();
        loadedPurchases.clear();

        List<String> auctionIds = response.getAuctions() == null ? List.of()
                : response.getAuctions().stream()
                .map(summary -> summary == null ? null : summary.getAuctionId())
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (auctionIds.isEmpty()) {
            allPurchases.clear();
            renderPurchaseCards(allPurchases);
            statusLabel.setText("No auctions available yet.");
            return;
        }

        pendingAuctionIds.addAll(auctionIds);
        statusLabel.setText("Loading details for " + pendingAuctionIds.size() + " auction(s)...");
        for (String auctionId : auctionIds) {
            ClientApp.getInstance().getNetworkService().sendMessage(new GetAuctionDetailsRequestMessage(auctionId));
        }
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
            statusLabel.setText("Loading details for " + pendingAuctionIds.size() + " auction(s)...");
            return;
        }

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

    private void handleErrorResponse(Message message) {
        if (!(message instanceof ErrorMessage errorMessage)) {
            return;
        }
        statusLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-size: 12px; -fx-font-weight: bold;");
        statusLabel.setText(errorMessage.getErrorMessage());
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

        renderPurchaseCards(filtered);
        statusLabel.setStyle(STATUS_TEXT_STYLE);
        statusLabel.setText("Showing " + filtered.size() + " of " + allPurchases.size() + " purchases.");
    }

    private void renderPurchaseCards(List<PurchaseCard> purchases) {
        purchaseFlowPane.getChildren().clear();
        if (purchases.isEmpty()) {
            Label emptyLabel = new Label("You do not have any purchases yet.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            purchaseFlowPane.getChildren().add(emptyLabel);
            return;
        }
        for (PurchaseCard purchase : purchases) {
            purchaseFlowPane.getChildren().add(createPurchaseCard(purchase));
        }
    }

    private VBox createPurchaseCard(PurchaseCard purchase) {
        VBox card = new VBox(8.0);
        card.setPrefSize(250.0, 220.0);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0); "
                + "-fx-padding: 10;");

        Label title = new Label(purchase.title());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label orderStatus = new Label("Order status: " + purchase.purchaseStatus());
        orderStatus.setStyle("-fx-text-fill: " + statusColor(purchase.purchaseStatus()) + "; -fx-font-weight: bold;");
        Label finalPrice = new Label("Final price: " + formatMoney(purchase.finalPrice()));
        Label seller = new Label("Seller: " + safeText(purchase.sellerId()));
        Label endedAt = new Label("Ended at: " + formatEndTime(purchase.endTime()));

        Button viewButton = new Button("View auction");
        viewButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        viewButton.setOnAction(event -> {
            statusLabel.setText("Opening purchase: " + purchase.title());
            ClientApp.getInstance().setSelectedAuctionId(purchase.auctionId());
            SceneNavigator.switchScene("AuctionItem");
        });

        card.getChildren().addAll(title, orderStatus, finalPrice, seller, endedAt, viewButton);
        return card;
    }

    private Optional<PurchaseCard> toPurchaseCard(AuctionDetailsResponseMessage response, String currentUsername) {
        if (response == null || currentUsername == null || currentUsername.isBlank()) {
            return Optional.empty();
        }
        if (response.getWinnerBidderId() == null || !response.getWinnerBidderId().equalsIgnoreCase(currentUsername)) {
            return Optional.empty();
        }

        String purchaseStatus = derivePurchaseStatus(response.getStatus());
        if (purchaseStatus == null) {
            return Optional.empty();
        }
        return Optional.of(new PurchaseCard(
                response.getAuctionId(),
                response.getTitle(),
                response.getSellerId(),
                response.getCurrentPrice(),
                purchaseStatus,
                response.getEndTime()
        ));
    }

    private String resolveCurrentUsername() {
        if (ClientApp.getInstance() == null || ClientApp.getInstance().getCurrentUsername() == null
                || ClientApp.getInstance().getCurrentUsername().isBlank()) {
            return "";
        }
        return ClientApp.getInstance().getCurrentUsername();
    }

    private String derivePurchaseStatus(AuctionStatus status) {
        if (status == AuctionStatus.PAID) {
            return STATUS_PAID;
        }
        if (status == AuctionStatus.FINISHED) {
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
            LocalDateTime endTime
    ) {
    }
}
