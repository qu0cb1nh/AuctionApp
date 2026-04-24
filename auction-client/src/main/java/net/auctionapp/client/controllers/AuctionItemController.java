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
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.BidResultMessage;
import net.auctionapp.common.messages.types.BidView;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
    @FXML private Label timeRemainingLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button placeBidButton;
    @FXML private Label messageLabel;
    @FXML private LineChart<String, Number> priceHistoryChart;

    private String currentAuctionId;
    private BigDecimal currentHighestBid = BigDecimal.ZERO;
    private LocalDateTime auctionEndTime;
    private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
    private Consumer<Message> detailsHandler;
    private Consumer<Message> bidAcceptedHandler;
    private Consumer<Message> bidRejectedHandler;
    private Consumer<Message> priceUpdateHandler;
    private boolean handlersRegistered;

    @FXML
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Auction Details", true, "AuctionList");
        messageLabel.setText("");
        priceHistoryChart.getData().clear();
        priceSeries.setName("Bid price");
        priceHistoryChart.getData().add(priceSeries);
        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                cleanupHandlers();
            }
        });

        currentAuctionId = ClientApp.getInstance().getSelectedAuctionId();
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            setErrorMessage("No auction selected.");
            placeBidButton.setDisable(true);
            return;
        }

        registerMessageHandlers();
        ClientApp.getInstance().getNetworkService().sendMessage(new GetAuctionDetailsRequestMessage(currentAuctionId));
    }

    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            BigDecimal bid = parseBidAmount(bidAmountField.getText());
            BidRequestMessage request = new BidRequestMessage(
                    currentAuctionId,
                    bid.doubleValue(),
                    ClientApp.getInstance().getCurrentUsername()
            );
            ClientApp.getInstance().getNetworkService().sendMessage(request);
        } catch (NumberFormatException e) {
            setErrorMessage("Please enter a valid number.");
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        cleanupHandlers();
        SceneNavigator.switchScene("AuctionList");
    }

    private void registerMessageHandlers() {
        detailsHandler = this::handleAuctionDetails;
        bidAcceptedHandler = this::handleBidAccepted;
        bidRejectedHandler = this::handleBidRejected;
        priceUpdateHandler = this::handlePriceUpdate;
        ClientApp.getInstance().addMessageHandler(MessageType.AUCTION_DETAILS_RESPONSE, detailsHandler);
        ClientApp.getInstance().addMessageHandler(MessageType.BID_ACCEPTED, bidAcceptedHandler);
        ClientApp.getInstance().addMessageHandler(MessageType.BID_REJECTED, bidRejectedHandler);
        ClientApp.getInstance().addMessageHandler(MessageType.PRICE_UPDATE, priceUpdateHandler);
        handlersRegistered = true;
    }

    private void cleanupHandlers() {
        if (!handlersRegistered) {
            return;
        }
        if (detailsHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.AUCTION_DETAILS_RESPONSE, detailsHandler);
        }
        if (bidAcceptedHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.BID_ACCEPTED, bidAcceptedHandler);
        }
        if (bidRejectedHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.BID_REJECTED, bidRejectedHandler);
        }
        if (priceUpdateHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.PRICE_UPDATE, priceUpdateHandler);
        }
        handlersRegistered = false;
    }

    private void handleAuctionDetails(Message message) {
        if (!(message instanceof AuctionDetailsResponseMessage response)) {
            return;
        }
        if (!currentAuctionId.equals(response.getAuctionId())) {
            return;
        }

        productNameLabel.setText(response.getTitle());
        descriptionLabel.setText(response.getDescription());
        currentHighestBid = response.getCurrentPrice() == null ? BigDecimal.ZERO : response.getCurrentPrice();
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        auctionEndTime = response.getEndTime();
        timeRemainingLabel.setText(formatTimeRemaining(auctionEndTime));
        renderBidHistory(response.getBidHistory());
        setInfoMessage("Place a bid higher than $" + currentHighestBid.toPlainString());
    }

    private void handleBidAccepted(Message message) {
        if (!(message instanceof BidResultMessage result)) {
            return;
        }
        if (!currentAuctionId.equals(result.getAuctionId())) {
            return;
        }
        if (result.getCurrentPrice() != null) {
            currentHighestBid = result.getCurrentPrice();
            currentBidLabel.setText("$" + currentHighestBid.toPlainString());
            addChartPoint(currentHighestBid);
        }
        setSuccessMessage(result.getMessage());
        bidAmountField.clear();
    }

    private void handleBidRejected(Message message) {
        if (!(message instanceof BidResultMessage result)) {
            return;
        }
        if (!currentAuctionId.equals(result.getAuctionId())) {
            return;
        }
        setErrorMessage(result.getMessage());
    }

    private void handlePriceUpdate(Message message) {
        if (!(message instanceof PriceUpdateMessage update)) {
            return;
        }
        if (!currentAuctionId.equals(update.getItemId())) {
            return;
        }
        currentHighestBid = BigDecimal.valueOf(update.getNewPrice());
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        addChartPoint(currentHighestBid);
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

    private void addChartPoint(BigDecimal bid) {
        priceSeries.getData().add(new XYChart.Data<>(LocalDateTime.now().toLocalTime().toString(), bid));
    }

    private BigDecimal parseBidAmount(String value) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException("empty");
        }
        BigDecimal bid = new BigDecimal(value.trim());
        if (bid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NumberFormatException("non-positive");
        }
        return bid;
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
