package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.services.WatchListService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.AuctionDisplayUtil;
import net.auctionapp.client.utils.DurationFormatUtil;
import net.auctionapp.client.utils.FxViewUtil;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auction.AuctionActionResponseMessage;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.dto.BidDto;
import net.auctionapp.common.messages.auction.AuctionEndedResponseMessage;
import net.auctionapp.common.messages.auction.AuctionUpdatedResponseMessage;
import net.auctionapp.common.messages.auction.BidResponseMessage;
import net.auctionapp.common.messages.auction.PriceUpdateResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.utils.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuctionItemMenuController implements AuctionContextController {
    private static final int MAX_VISIBLE_BIDS = 16;
    private static final DateTimeFormatter CHART_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TOOLTIP_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss");
    private static final PseudoClass WATCHING_STATE = PseudoClass.getPseudoClass("watching");
    private static final PseudoClass RUNNING_STATE = PseudoClass.getPseudoClass("running");
    private static final PseudoClass URGENT_STATE = PseudoClass.getPseudoClass("urgent");
    private static final PseudoClass ENDED_STATE = PseudoClass.getPseudoClass("ended");
    private static final PseudoClass PAID_STATE = PseudoClass.getPseudoClass("paid");
    private static final PseudoClass CANCELED_STATE = PseudoClass.getPseudoClass("canceled");
    private static final PseudoClass NEUTRAL_STATE = PseudoClass.getPseudoClass("neutral");

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
    private Button cancelBidsButton;
    @FXML
    private Button watchListButton;
    @FXML
    private VBox bidSection;
    @FXML
    private LineChart<String, Number> priceHistoryChart;
    @FXML
    private NumberAxis priceAxis;
    @FXML
    private Label chartBidCountLabel;
    @FXML
    private Label chartStatusLabel;

    private String currentAuctionId;
    private BigDecimal currentHighestBid = BigDecimal.ZERO;
    private BigDecimal minimumNextBid = BigDecimal.ZERO;
    private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
    private LocalDateTime auctionEndTime;
    private Timeline countdownTimeline;
    private boolean closeRefreshRequested;
    private boolean inWatchList;
    private boolean watchListStateLoaded;
    private boolean bidActionAvailable;
    private boolean bidRequestPending;
    private boolean cancelBidsRequestPending;
    private String currentLeadingBidderId;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Auction details");
        updateWatchListButton();
        updateCancelBidsButtonState();
        priceHistoryChart.getData().clear();
        priceSeries.setName("Bid price");
        priceHistoryChart.getData().add(priceSeries);
        priceAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return formatAxisPrice(value);
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        rootPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                stopCountdownTimer();
            }
        });
    }

    @Override
    public void setAuctionId(String auctionId) {
        currentAuctionId = auctionId;
        closeRefreshRequested = false;
        watchListStateLoaded = false;
        updateWatchListButton();
        updateCancelBidsButtonState();
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            showError("No auction selected.");
            placeBidButton.setDisable(true);
            updateCancelBidsButtonState();
            return;
        }

        registerEventListeners();
        AuctionService.getInstance().observeAuction(currentAuctionId, true);
        String subscribedAuctionId = currentAuctionId;
        SceneManager.registerSceneCleanup(() -> AuctionService.getInstance().observeAuction(subscribedAuctionId, false));
        startCountdownTimer();
        requestAuctionDetails();
        WatchListService.getInstance().requestWatchList(this::handleWatchListResponse);
    }

    @FXML
    public void handlePlaceBid() {
        try {
            BigDecimal bid = parseBidAmount(bidAmountField.getText(), minimumNextBid);
            setBidRequestPending(true);
            AuctionService.getInstance().placeBid(currentAuctionId, bid, this::handleBidResult);
        } catch (IllegalArgumentException | ValidationException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    public void handleCancelBids() {
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            showError("No auction selected.");
            return;
        }
        ClientSession session = ClientSession.getInstance();
        if (session.getUserId() != null
                && session.getUserId().equalsIgnoreCase(currentLeadingBidderId)) {
            showError("You cannot cancel your leading bid.");
            return;
        }
        setCancelBidsRequestPending(true);
        AuctionService.getInstance().cancelBids(currentAuctionId, this::handleCancelBidsResult);
    }

    @FXML
    public void handleToggleWatchList() {
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            return;
        }
        WatchListService.getInstance().updateWatched(currentAuctionId, !inWatchList, this::handleWatchListUpdateResponse);
    }

    private void registerEventListeners() {
        SceneManager.registerSceneMessageListener(MessageType.PRICE_UPDATE, this::handlePriceUpdate);
        SceneManager.registerSceneMessageListener(MessageType.AUCTION_ENDED, this::handleAuctionEnded);
        SceneManager.registerSceneMessageListener(MessageType.AUCTION_UPDATED, this::handleAuctionUpdated);
        SceneManager.registerSceneMessageListener(MessageType.WATCH_LIST_CHANGED, this::handleWatchListChanged);
    }

    private void requestAuctionDetails() {
        AuctionService.getInstance().requestAuctionDetails(currentAuctionId, this::handleAuctionDetailsResponse);
    }

    private void handleAuctionDetailsResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showError(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionDetailsResponseMessage response)) {
            showError("Unexpected response from server.");
            return;
        }
        if (!currentAuctionId.equals(response.getAuctionId())) {
            return;
        }

        productNameLabel.setText(response.getTitle());
        descriptionLabel.setText(response.getDescription());
        productImageView.setImage(new Image(
                response.getImageUrl() == null || response.getImageUrl().isBlank()
                        ? ResourcesUtil.itemPlaceholder(response.getItemType()).toExternalForm()
                        : response.getImageUrl(),
                true
        ));
        currentHighestBid = response.getCurrentPrice() == null ? BigDecimal.ZERO : response.getCurrentPrice();
        minimumNextBid = response.getMinimumNextBid() == null ? currentHighestBid : response.getMinimumNextBid();
        currentLeadingBidderId = response.getLeadingBidderId();
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        minimumNextBidLabel.setText("$" + minimumNextBid.stripTrailingZeros().toPlainString());
        leadingBidderLabel.setText(AuctionDisplayUtil.formatBidder(response.getLeadingBidderUsername()));
        closeRefreshRequested = AuctionDisplayUtil.isClosed(response);
        setAuctionEndTime(response.getEndTime());
        updateAuctionStatusLabel(response);
        renderBidHistory(response.getBidHistory());
        updateBidControls(response);
    }

    private void handleWatchListResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showError(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof WatchListResponseMessage response)) {
            showError("Unexpected watch list response from server.");
            return;
        }
        inWatchList = response.getAuctions().stream()
                .anyMatch(auction -> auction != null && currentAuctionId.equals(auction.getAuctionId()));
        watchListStateLoaded = true;
        updateWatchListButton();
    }

    private void handleWatchListUpdateResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            showError(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof WatchListChangedResponseMessage changed)) {
            showError("Unexpected watch list response from server.");
            return;
        }
        handleWatchListChanged(changed);
        NotificationToastManager.showSuccess(AuctionDisplayUtil.watchListActionMessage(changed.isWatched()));
    }

    private void handleWatchListChanged(WatchListChangedResponseMessage changed) {
        if (changed == null || !currentAuctionId.equals(changed.getAuctionId())) {
            return;
        }
        inWatchList = changed.isWatched();
        watchListStateLoaded = true;
        updateWatchListButton();
    }

    private void updateWatchListButton() {
        watchListButton.setDisable(
                currentAuctionId == null || currentAuctionId.isBlank() || !watchListStateLoaded
        );
        watchListButton.setText(inWatchList ? "Watching" : "Add to watchlist");
        watchListButton.pseudoClassStateChanged(WATCHING_STATE, inWatchList);
    }

    private void handleBidResult(Message message) {
        setBidRequestPending(false);
        if (message instanceof ErrorResponseMessage errorMessage) {
            showError(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof BidResponseMessage result)) {
            showError("Unexpected response from server.");
            return;
        }
        if (!currentAuctionId.equals(result.getAuctionId())) {
            return;
        }

        if (result.getType() == MessageType.BID_ACCEPTED) {
            if (result.getCurrentPrice() != null) {
                currentHighestBid = result.getCurrentPrice();
                currentBidLabel.setText("$" + currentHighestBid.toPlainString());
            }
            setAuctionEndTime(result.getEndTime());
            NotificationToastManager.showSuccess(result.getMessage());
            bidAmountField.clear();
            requestAuctionDetails();
            return;
        }
        if (result.getType() == MessageType.BID_REJECTED) {
            String rejectionMessage = "Bid was rejected.";
            if(result.getMessage() != null)
                rejectionMessage += result.getMessage();
            showError(rejectionMessage);
            bidAmountField.clear();
            requestAuctionDetails();
            return;
        }
        showError("Unexpected bid response from server.");
    }

    private void handleCancelBidsResult(Message message) {
        setCancelBidsRequestPending(false);
        if (message instanceof ErrorResponseMessage errorMessage) {
            showError(errorMessage.getErrorMessage());
            requestAuctionDetails();
            return;
        }
        if (!(message instanceof AuctionActionResponseMessage response)) {
            showError("Unexpected response from server.");
            return;
        }

        NotificationToastManager.showSuccess(response.getMessage());
        requestAuctionDetails();
    }

    private void handlePriceUpdate(PriceUpdateResponseMessage update) {
        if (!currentAuctionId.equals(update.getAuctionId())) {
            return;
        }
        if (update.getNewPrice() == null) {
            return;
        }
        currentHighestBid = update.getNewPrice();
        currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        leadingBidderLabel.setText(AuctionDisplayUtil.formatBidder(update.getLeadingUserName()));
        setAuctionEndTime(update.getEndTime());
        requestAuctionDetails();
    }

    private void handleAuctionEnded(AuctionEndedResponseMessage update) {
        if (!currentAuctionId.equals(update.getAuctionId())) {
            return;
        }
        if (update.getFinalPrice() != null) {
            currentHighestBid = update.getFinalPrice();
            currentBidLabel.setText("$" + currentHighestBid.toPlainString());
        }
        closeRefreshRequested = true;
        applyClosedBidState();
        requestAuctionDetails();
    }

    private void handleAuctionUpdated(AuctionUpdatedResponseMessage update) {
        if (currentAuctionId.equals(update.getAuctionId())) {
            requestAuctionDetails();
        }
    }

    private void renderBidHistory(List<BidDto> bidHistory) {
        priceSeries.getData().clear();
        List<BidDto> validBids = bidHistory.stream()
                .filter(bid -> bid != null && bid.getAmount() != null && bid.getTimestamp() != null)
                .toList();
        int totalBids = validBids.size();
        chartBidCountLabel.setText(totalBids + (totalBids == 1 ? " bid" : " bids"));
        if (totalBids == 0) {
            chartStatusLabel.setText("No bids yet. The live price trend will appear here.");
            return;
        }

        int skipCount = Math.max(0, totalBids - MAX_VISIBLE_BIDS);
        int validIndex = 0;
        Map<String, Integer> timeOccurrences = new HashMap<>();
        for (BidDto bid : validBids) {
            if (validIndex++ < skipCount) {
                continue;
            }
            String baseTime = bid.getTimestamp().format(CHART_TIME_FORMAT);
            int occurrence = timeOccurrences.merge(baseTime, 1, Integer::sum);
            String displayedTime = occurrence == 1 ? baseTime : baseTime + " +" + (occurrence - 1);
            XYChart.Data<String, Number> point = new XYChart.Data<>(displayedTime, bid.getAmount());
            priceSeries.getData().add(point);
            installPriceTooltip(point, bid);
        }
        chartStatusLabel.setText(skipCount == 0
                ? "Updating live as new bids arrive"
                : "Showing the latest " + MAX_VISIBLE_BIDS + " bids");
    }

    private void installPriceTooltip(XYChart.Data<String, Number> point, BidDto bid) {
        Tooltip tooltip = new Tooltip(
                "$" + bid.getAmount().stripTrailingZeros().toPlainString()
                        + "\n" + bid.getTimestamp().format(TOOLTIP_TIME_FORMAT)
        );
        point.nodeProperty().addListener((observable, oldNode, newNode) -> {
            if (newNode != null) {
                Tooltip.install(newNode, tooltip);
            }
        });
        if (point.getNode() != null) {
            Tooltip.install(point.getNode(), tooltip);
        }
    }

    private String formatAxisPrice(Number value) {
        double amount = value.doubleValue();
        if (Math.abs(amount) >= 1_000_000) {
            return String.format(Locale.US, "$%.1fM", amount / 1_000_000);
        }
        if (Math.abs(amount) >= 1_000) {
            return String.format(Locale.US, "$%.1fk", amount / 1_000);
        }
        return String.format(Locale.US, "$%.0f", amount);
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
        boolean closedAuction = AuctionDisplayUtil.isClosed(response);
        FxViewUtil.setVisible(bidSection, !closedAuction);
        if (closedAuction) {
            applyClosedBidState();
            return;
        }
        if (!session.isAuthenticated()) {
            bidActionAvailable = false;
            updatePlaceBidButtonState();
            return;
        }
        String currentUserId = session.getUserId();
        boolean isSellerViewingOwnAuction = response.getSellerId() != null
                && response.getSellerId().equalsIgnoreCase(currentUserId);

        if (isSellerViewingOwnAuction) {
            bidActionAvailable = false;
            updatePlaceBidButtonState();
            return;
        }

        if (response.getStatus() != AuctionStatus.RUNNING) {
            bidActionAvailable = false;
            updatePlaceBidButtonState();
            return;
        }

        bidActionAvailable = true;
        updatePlaceBidButtonState();
        if (minimumNextBid != null) {
            bidAmountField.setPromptText("At least $" + minimumNextBid.stripTrailingZeros().toPlainString());
        }
    }

    private String formatTimeRemaining(LocalDateTime endTime) {
        if (endTime == null) {
            return "N/A";
        }
        java.time.Duration duration = java.time.Duration.between(LocalDateTime.now(), endTime);
        if (duration.isNegative() || duration.isZero()) {
            return "Ended";
        }
        return DurationFormatUtil.formatRemainingDuration(duration);
    }

    private void updateTimeRemainingStyle(LocalDateTime endTime) {
        timeRemainingLabel.pseudoClassStateChanged(RUNNING_STATE, false);
        timeRemainingLabel.pseudoClassStateChanged(URGENT_STATE, false);
        timeRemainingLabel.pseudoClassStateChanged(ENDED_STATE, false);
        if (endTime == null) {
            return;
        }
        java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), endTime);
        if (remaining.isNegative() || remaining.isZero()) {
            timeRemainingLabel.pseudoClassStateChanged(ENDED_STATE, true);
            return;
        }
        if (remaining.toMinutes() < 5) {
            timeRemainingLabel.pseudoClassStateChanged(URGENT_STATE, true);
            return;
        }
        timeRemainingLabel.pseudoClassStateChanged(RUNNING_STATE, true);
    }

    private void updateAuctionStatusLabel(AuctionDetailsResponseMessage response) {
        String displayStatus = AuctionDisplayUtil.displayStatus(response);
        auctionStatusLabel.setText(displayStatus);
        auctionStatusLabel.pseudoClassStateChanged(RUNNING_STATE, "RUNNING".equals(displayStatus));
        auctionStatusLabel.pseudoClassStateChanged(PAID_STATE, "PAID".equals(displayStatus));
        auctionStatusLabel.pseudoClassStateChanged(CANCELED_STATE, "CANCELED".equals(displayStatus));
        auctionStatusLabel.pseudoClassStateChanged(NEUTRAL_STATE, "N/A".equals(displayStatus));
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
        if (auctionEndTime != null
                && !LocalDateTime.now().isBefore(auctionEndTime)
                && !closeRefreshRequested
                && currentAuctionId != null) {
            closeRefreshRequested = true;
            applyClosedBidState();
            requestAuctionDetails();
        }
    }

    private void applyClosedBidState() {
        FxViewUtil.setVisible(bidSection, false);
        bidActionAvailable = false;
        updatePlaceBidButtonState();
        stopCountdownTimer();
        timeRemainingLabel.setText("Ended");
        updateTimeRemainingStyle(LocalDateTime.now());
    }

    private void updatePlaceBidButtonState() {
        placeBidButton.setDisable(!bidActionAvailable || bidRequestPending);
    }

    private void setBidRequestPending(boolean pending) {
        bidRequestPending = pending;
        updatePlaceBidButtonState();
    }

    private void setCancelBidsRequestPending(boolean pending) {
        cancelBidsRequestPending = pending;
        updateCancelBidsButtonState();
    }

    private void updateCancelBidsButtonState() {
        cancelBidsButton.setDisable(currentAuctionId == null || currentAuctionId.isBlank() || cancelBidsRequestPending);
        cancelBidsButton.setText(cancelBidsRequestPending ? "Canceling..." : "Cancel My Bids");
    }

    private void showError(String text) {
        NotificationToastManager.showError(text == null || text.isBlank() ? "Unexpected error." : text);
    }
}
