package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.BidResultMessage;
import net.auctionapp.common.messages.types.BidView;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.utils.MoneyUtil;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class AuctionItemMenuController implements Initializable {
    @FXML
    private HeaderController appHeaderController;
    @FXML
    private BorderPane rootPane;
    @FXML
    private ImageView productImageView;
    @FXML
    private Label productNameLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private Label currentBidLabel;
    @FXML
    private Label leadingBidderLabel;
    @FXML
    private Label auctionStatusLabel;
    @FXML
    private Label timeRemainingLabel;
    @FXML
    private Label minimumNextBidLabel;
    @FXML
    private TextField bidAmountField;
    @FXML
    private Button placeBidButton;
    @FXML
    private VBox bidSection;
    @FXML
    private Label messageLabel;
    @FXML
    private LineChart<String, Number> priceHistoryChart;

    private String currentAuctionId;
    private BigDecimal currentHighestBid = BigDecimal.ZERO;
    private BigDecimal minimumNextBid = BigDecimal.ZERO;
    private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
    private LocalDateTime auctionEndTime;
    private Timeline countdownTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Auction Details");
        messageLabel.setText("");
        placeBidButton.setDisable(true);
        auctionStatusLabel.setText("N/A");
        minimumNextBidLabel.setText("N/A");
        priceHistoryChart.getData().clear();
        priceSeries.setName("Bid price");
        priceHistoryChart.getData().add(priceSeries);

        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                stopCountdownTimer();
            }
        });
        currentAuctionId = ClientApp.getInstance().getSelectedAuctionId();
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            setErrorMessage("No auction selected.");
            placeBidButton.setDisable(true);
            return;
        }

        registerEventListeners();
        startCountdownTimer();
        requestAuctionDetails();
    }

    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            BigDecimal bid = parseBidAmount(bidAmountField.getText(), minimumNextBid);
            AuctionService.getInstance().placeBid(currentAuctionId, bid, this::handleBidResult);
        } catch (IllegalArgumentException e) {
            setErrorMessage(e.getMessage());
        }
    }

    @FXML
    public void handleUseMinimumBid(ActionEvent event) {
        if (minimumNextBid == null || minimumNextBid.compareTo(BigDecimal.ZERO) <= 0) {
            setErrorMessage("Minimum next bid is not available.");
            return;
        }
        bidAmountField.setText(minimumNextBid.stripTrailingZeros().toPlainString());
        setInfoMessage("Minimum next bid has been filled in.");
    }

    private void registerEventListeners() {
        SceneManager.registerSceneMessageListener(MessageType.PRICE_UPDATE, this::handlePriceUpdate);
    }

    private void requestAuctionDetails() {
        AuctionService.getInstance().requestAuctionDetails(currentAuctionId, this::handleAuctionDetailsResponse);
    }

    private void handleAuctionDetailsResponse(Message message) {
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
        updateProductImage(response.getImageUrl());
        currentHighestBid = response.getCurrentPrice() == null ? BigDecimal.ZERO : response.getCurrentPrice();
        minimumNextBid = response.getMinimumNextBid() == null ? currentHighestBid : response.getMinimumNextBid();
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        minimumNextBidLabel.setText("$" + minimumNextBid.stripTrailingZeros().toPlainString());
        leadingBidderLabel.setText(formatTopBidder(response.getLeadingBidderId()));
        setAuctionEndTime(response.getEndTime());
        updateAuctionStatusLabel(response);
        renderBidHistory(response.getBidHistory());
        updateBidControls(response);
    }

    private void handleBidResult(Message message) {
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
            setAuctionEndTime(result.getEndTime());
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

    private void handlePriceUpdate(PriceUpdateMessage update) {
        if (!currentAuctionId.equals(update.getAuctionId())) {
            return;
        }
        currentHighestBid = BigDecimal.valueOf(update.getNewPrice());
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        leadingBidderLabel.setText(formatTopBidder(update.getLeadingUserName()));
        setAuctionEndTime(update.getEndTime());
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

    private void updateProductImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        Image image = new Image(imageUrl, true);
        productImageView.setImage(image);
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
        MoneyUtil.requirePositiveMoney(bid, "Bid amount");
        if (minimumAllowed != null && bid.compareTo(minimumAllowed) < 0) {
            throw new IllegalArgumentException("Bid must be at least $" + minimumAllowed.toPlainString() + ".");
        }
        return bid;
    }

    private void updateBidControls(AuctionDetailsResponseMessage response) {
        ClientSession session = ClientSession.getInstance();
        boolean closedAuction = isClosedAuction(response);
        setBidSectionVisible(!closedAuction);
        if (closedAuction) {
            placeBidButton.setDisable(true);
            messageLabel.setText("");
            return;
        }
        if (!session.isAuthenticated()) {
            placeBidButton.setDisable(true);
            setInfoMessage("Please log in before bidding.");
            return;
        }
        String currentUserId = session.getUserId();
        boolean isSellerViewingOwnAuction = response.getSellerId() != null
                && currentUserId != null
                && response.getSellerId().equalsIgnoreCase(currentUserId);

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
        bidAmountField.setPromptText("At least $" + minimumNextBid.stripTrailingZeros().toPlainString());
        setInfoMessage("Place a bid of at least $" + minimumNextBid.toPlainString() + ".");
    }

    private String formatTimeRemaining(LocalDateTime endTime) {
        if (endTime == null) {
            return "N/A";
        }
        java.time.Duration duration = java.time.Duration.between(LocalDateTime.now(), endTime);
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

    private void updateTimeRemainingStyle(LocalDateTime endTime) {
        if (endTime == null) {
            timeRemainingLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333333;");
            return;
        }
        java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), endTime);
        if (remaining.isNegative() || remaining.isZero()) {
            timeRemainingLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #6b7280;");
            return;
        }
        if (remaining.toMinutes() < 5) {
            timeRemainingLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #c13c21;");
            return;
        }
        timeRemainingLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1f8f4c;");
    }

    private void updateAuctionStatusLabel(AuctionDetailsResponseMessage response) {
        String displayStatus = deriveDisplayStatus(response);
        String color = switch (displayStatus) {
            case "RUNNING" -> "#1f8f4c";
            case "PAID" -> "#2e7d32";
            case "CANCELED" -> "#6b7280";
            default -> "#3f5569";
        };
        auctionStatusLabel.setText(displayStatus);
        auctionStatusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
    }

    private String deriveDisplayStatus(AuctionDetailsResponseMessage response) {
        if (response == null || response.getStatus() == null) {
            return "N/A";
        }
        if (response.getStatus() == AuctionStatus.CANCELED) {
            return "CANCELED";
        }
        if (response.getStatus() == AuctionStatus.PAID) {
            return "PAID";
        }
        if (isClosedAuction(response)) {
            return hasWinner(response.getLeadingBidderId(), response.getWinnerBidderId()) ? "PAID" : "CANCELED";
        }
        return "RUNNING";
    }

    private boolean isClosedAuction(AuctionDetailsResponseMessage response) {
        if (response == null || response.getStatus() == null) {
            return false;
        }
        if (response.getStatus() == AuctionStatus.PAID || response.getStatus() == AuctionStatus.CANCELED) {
            return true;
        }
        return response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && !LocalDateTime.now().isBefore(response.getEndTime());
    }

    private boolean hasWinner(String leadingBidderId, String winnerBidderId) {
        return (winnerBidderId != null && !winnerBidderId.isBlank())
                || (leadingBidderId != null && !leadingBidderId.isBlank());
    }

    private void setAuctionEndTime(LocalDateTime endTime) {
        if (endTime == null) {
            return;
        }
        auctionEndTime = endTime;
        refreshTimeRemaining();
    }

    private void startCountdownTimer() {
        stopCountdownTimer();
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshTimeRemaining()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void stopCountdownTimer() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private void refreshTimeRemaining() {
        timeRemainingLabel.setText(formatTimeRemaining(auctionEndTime));
        updateTimeRemainingStyle(auctionEndTime);
    }

    private void setBidSectionVisible(boolean visible) {
        bidSection.setManaged(visible);
        bidSection.setVisible(visible);
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