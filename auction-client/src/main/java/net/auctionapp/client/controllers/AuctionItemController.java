package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import net.auctionapp.client.ClientApp;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.BidResultMessage;
import net.auctionapp.common.messages.types.BidView;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.models.auction.AuctionStatus;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class AuctionItemController implements Initializable {
    @FXML
    private HeaderController appHeaderController;

    @FXML private BorderPane rootPane;
    @FXML private ImageView productImageView;
    @FXML private Label productNameLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label currentBidLabel;
    @FXML private Label leadingBidderLabel;
    @FXML private Label timeRemainingLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button placeBidButton;
    @FXML private Label messageLabel;
    @FXML private LineChart<String, Number> priceHistoryChart;

    private String currentAuctionId;
    private BigDecimal currentHighestBid = BigDecimal.ZERO;
    private BigDecimal minimumNextBid = BigDecimal.ZERO;
    private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
    private Consumer<Message> priceUpdateHandler;
    private boolean priceUpdateHandlerRegistered;

    @FXML
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Auction Details", true, "AuctionList");
        messageLabel.setText("");
        placeBidButton.setDisable(true);
        priceHistoryChart.getData().clear();
        priceSeries.setName("Bid price");
        priceHistoryChart.getData().add(priceSeries);
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                cleanupEventHandlers();
            }
        });

        currentAuctionId = ClientApp.getInstance().getSelectedAuctionId();
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            setErrorMessage("No auction selected.");
            placeBidButton.setDisable(true);
            return;
        }

        registerEventHandlers();
        requestAuctionDetails();
    }

    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            BigDecimal bid = parseBidAmount(bidAmountField.getText(), minimumNextBid);
            BidRequestMessage request = new BidRequestMessage(currentAuctionId, bid.doubleValue());
            ClientApp.getInstance().sendRequest(request, this::handleBidResult);
        } catch (IllegalArgumentException e) {
            setErrorMessage(e.getMessage());
        }
    }

    private void registerEventHandlers() {
        priceUpdateHandler = this::handlePriceUpdate;
        ClientApp.getInstance().addMessageHandler(MessageType.PRICE_UPDATE, priceUpdateHandler);
        priceUpdateHandlerRegistered = true;
    }

    private void cleanupEventHandlers() {
        if (!priceUpdateHandlerRegistered) {
            return;
        }
        if (priceUpdateHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.PRICE_UPDATE, priceUpdateHandler);
        }
        priceUpdateHandlerRegistered = false;
    }

    private void requestAuctionDetails() {
        ClientApp.getInstance().sendRequest(
                new GetAuctionDetailsRequestMessage(currentAuctionId),
                this::handleAuctionDetailsResponse
        );
    }

    private void handleAuctionDetailsResponse(Message message, Throwable throwable) {
        if (throwable != null) {
            setErrorMessage("Failed to load auction details: " + throwable.getMessage());
            return;
        }
        if (message instanceof ErrorMessage errorMessage) {
            setErrorMessage(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionDetailsResponseMessage response)) {
            setErrorMessage("Unexpected response from server.");
            return;
        }
        if (!currentAuctionId.equals(response.getAuctionId())) {
            return;
        }

        productNameLabel.setText(response.getTitle());
        descriptionLabel.setText(response.getDescription());
        currentHighestBid = response.getCurrentPrice() == null ? BigDecimal.ZERO : response.getCurrentPrice();
        minimumNextBid = response.getMinimumNextBid() == null ? currentHighestBid : response.getMinimumNextBid();
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        leadingBidderLabel.setText(formatTopBidder(response.getLeadingBidderId()));
        LocalDateTime auctionEndTime = response.getEndTime();
        timeRemainingLabel.setText(formatTimeRemaining(auctionEndTime));
        renderBidHistory(response.getBidHistory());
        updateBidControls(response);
    }

    private void handleBidResult(Message message, Throwable throwable) {
        if (throwable != null) {
            setErrorMessage("Failed to place bid: " + throwable.getMessage());
            return;
        }
        if (message instanceof ErrorMessage errorMessage) {
            setErrorMessage(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof BidResultMessage result)) {
            setErrorMessage("Unexpected response from server.");
            return;
        }
        if (!currentAuctionId.equals(result.getAuctionId())) {
            return;
        }

        if (message.getType() == MessageType.BID_ACCEPTED) {
            if (result.getCurrentPrice() != null) {
                currentHighestBid = result.getCurrentPrice();
                currentBidLabel.setText("$" + currentHighestBid.toPlainString());
            }
            setSuccessMessage(result.getMessage());
            bidAmountField.clear();
            requestAuctionDetails();
            return;
        }
        if (message.getType() == MessageType.BID_REJECTED) {
            setErrorMessage(result.getMessage());
            return;
        }
        setErrorMessage("Unexpected bid response from server.");
    }

    private void handlePriceUpdate(Message message) {
        if (!(message instanceof PriceUpdateMessage update)) {
            return;
        }
        if (!currentAuctionId.equals(update.getAuctionId())) {
            return;
        }
        currentHighestBid = BigDecimal.valueOf(update.getNewPrice());
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        leadingBidderLabel.setText(formatTopBidder(update.getLeadingUserName()));
        requestAuctionDetails();
        setInfoMessage("New highest bid by " + update.getLeadingUserName());
    }

    private void renderBidHistory(List<BidView> bidHistory) {
        priceSeries.getData().clear();
        if (bidHistory == null) {
            return;
        }
        for (BidView bid : bidHistory) {
            if (bid == null || bid.getAmount() == null || bid.getTimestamp() == null) {
                continue;
            }
            priceSeries.getData().add(new XYChart.Data<>(bid.getTimestamp().toLocalTime().toString(), bid.getAmount()));
        }
    }

    private BigDecimal parseBidAmount(String value, BigDecimal minimumAllowed) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Please enter a bid amount.");
        }
        BigDecimal bid;
        try {
            bid = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Please enter a valid number.");
        }
        if (bid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bid amount must be greater than zero.");
        }
        if (minimumAllowed != null && bid.compareTo(minimumAllowed) < 0) {
            throw new IllegalArgumentException("Bid must be at least $" + minimumAllowed.toPlainString() + ".");
        }
        return bid;
    }

    private void updateBidControls(AuctionDetailsResponseMessage response) {
        String currentUsername = ClientApp.getInstance() == null
                ? ""
                : ClientApp.getInstance().getCurrentUsername();
        boolean isSellerViewingOwnAuction = response.getSellerId() != null
                && response.getSellerId().equalsIgnoreCase(currentUsername);

        if (isSellerViewingOwnAuction) {
            placeBidButton.setDisable(true);
            setInfoMessage("You are the seller of this auction, so bidding is disabled.");
            return;
        }

        if (response.getStatus() != AuctionStatus.RUNNING) {
            placeBidButton.setDisable(true);
            String state = response.getStatus() == null
                    ? "unavailable"
                    : response.getStatus().name().toLowerCase(Locale.ROOT);
            setInfoMessage("Bidding is unavailable because the auction is " + state + ".");
            return;
        }

        placeBidButton.setDisable(false);
        if (minimumNextBid == null) {
            setInfoMessage("Place a bid higher than $" + currentHighestBid.toPlainString());
            return;
        }
        setInfoMessage("Place a bid of at least $" + minimumNextBid.toPlainString() + ".");
    }

    private String formatTimeRemaining(LocalDateTime endTime) {
        if (endTime == null) {
            return "N/A";
        }
        Duration duration = Duration.between(LocalDateTime.now(), endTime);
        if (duration.isNegative() || duration.isZero()) {
            return "Ended";
        }
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatTopBidder(String leadingBidderId) {
        if (leadingBidderId == null || leadingBidderId.isBlank()) {
            return "No bids yet";
        }
        return leadingBidderId;
    }

    private void setSuccessMessage(String text) {
        messageLabel.setText(text);
        messageLabel.setStyle("-fx-text-fill: green;");
    }

    private void setErrorMessage(String text) {
        messageLabel.setText(text);
        messageLabel.setStyle("-fx-text-fill: red;");
    }

    private void setInfoMessage(String text) {
        messageLabel.setText(text);
        messageLabel.setStyle("-fx-text-fill: #666666;");
    }
}
