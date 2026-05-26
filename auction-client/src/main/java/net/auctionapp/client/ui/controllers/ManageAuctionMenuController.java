package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.event.ActionEvent;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auction.AuctionActionResponseMessage;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.auction.AuctionEndedResponseMessage;
import net.auctionapp.common.messages.auction.AuctionUpdatedResponseMessage;
import net.auctionapp.common.messages.auction.PriceUpdateResponseMessage;
import net.auctionapp.common.messages.auction.UpdateAuctionRequestMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.auction.AuctionStatus;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.ResourceBundle;

public class ManageAuctionMenuController implements Initializable, AuctionContextController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SUCCESS_STATE = PseudoClass.getPseudoClass("success");

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private Label statusLabel;
    @FXML
    private Label auctionInfoLabel;
    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private TextField endTimeField;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button closeButton;

    private String currentAuctionId;
    private AuctionDetailsResponseMessage currentAuction;
    private boolean actionRequestPending;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Manage Auction");
        setInfoStatus("");
    }

    @Override
    public void setAuctionId(String auctionId) {
        currentAuctionId = auctionId;
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            setErrorStatus("No auction selected.");
            disableActions();
            return;
        }
        SceneManager.registerSceneMessageListener(MessageType.AUCTION_UPDATED, this::handleAuctionUpdated);
        SceneManager.registerSceneMessageListener(MessageType.PRICE_UPDATE, this::handlePriceUpdate);
        SceneManager.registerSceneMessageListener(MessageType.AUCTION_ENDED, this::handleAuctionEnded);
        AuctionService.getInstance().observeAuction(currentAuctionId, true);
        String observedAuctionId = currentAuctionId;
        SceneManager.registerSceneCleanup(() -> AuctionService.getInstance().observeAuction(observedAuctionId, false));
        requestAuctionDetails();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        requestAuctionDetails();
    }

    @FXML
    public void handleSave(ActionEvent event) {
        try {
            UpdateAuctionRequestMessage request = buildUpdateRequest();
            setActionRequestPending(true);
            AuctionService.getInstance().updateAuction(request, this::handleActionResponse);
        } catch (IllegalArgumentException e) {
            setErrorStatus(e.getMessage());
        }
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        if (currentAuctionId == null) {
            setErrorStatus("No auction selected.");
            disableActions();
            return;
        }
        if (confirmAction("Cancel Auction", "Cancel this auction? This action cannot be undone.")) {
            setActionRequestPending(true);
            AuctionService.getInstance().cancelAuction(currentAuctionId, this::handleActionResponse);
        }
    }

    @FXML
    public void handleClose(ActionEvent event) {
        if (currentAuctionId == null) {
            setErrorStatus("No auction selected.");
            disableActions();
            return;
        }
        if (confirmAction("Close Auction", "Close this auction now and determine its result?")) {
            setActionRequestPending(true);
            AuctionService.getInstance().closeAuction(currentAuctionId, this::handleActionResponse);
        }
    }

    private void requestAuctionDetails() {
        setInfoStatus("Loading auction details...");
        AuctionService.getInstance().requestAuctionDetails(currentAuctionId, this::handleAuctionDetailsResponse);
    }

    private void handleAuctionDetailsResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionDetailsResponseMessage response)) {
            setErrorStatus("Unexpected response from server.");
            return;
        }
        if (!currentAuctionId.equals(response.getAuctionId())) {
            return;
        }

        currentAuction = response;
        auctionInfoLabel.setText("Managing: " + response.getTitle() + " (" + deriveDisplayStatus(response) + ")");
        titleField.setText(response.getTitle());
        descriptionField.setText(response.getDescription());
        endTimeField.setText(formatDateTime(response.getEndTime()));
        ClientSession session = ClientSession.getInstance();
        boolean canManageAuction = session.canManageAuction(response.getSellerId());
        if (!canManageAuction) {
            setActionButtonsVisible(false);
            setFormEditable(false);
            setErrorStatus("Only the seller or an admin can manage this auction.");
            return;
        }
        configureActions(response, session.isAdmin());
        setInfoStatus("");
    }

    private void handleActionResponse(Message message) {
        setActionRequestPending(false);
        if (message instanceof ErrorResponseMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionActionResponseMessage result)) {
            setErrorStatus("Unexpected response from server.");
            return;
        }
        setSuccessStatus(result.getMessage());
        NotificationToastManager.showSuccess(result.getMessage());
        requestAuctionDetails();
    }

    private UpdateAuctionRequestMessage buildUpdateRequest() {
        if (currentAuction == null) {
            throw new IllegalArgumentException("Auction details are not loaded yet.");
        }
        boolean running = isRunning(currentAuction);
        if (!running) {
            throw new IllegalArgumentException("Only running auctions can be edited.");
        }
        String title = trim(titleField.getText());
        String description = trim(descriptionField.getText());
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }
        BigDecimal startingPrice = currentAuction.getStartingPrice();
        BigDecimal increment = currentIncrement();
        LocalDateTime startTime = currentAuction.getStartTime();
        LocalDateTime endTime = parseDateTime(endTimeField.getText(), "End time");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        if (endTime.isBefore(currentAuction.getEndTime())) {
            throw new IllegalArgumentException("End time cannot be earlier than the current end time.");
        }
        if (!endTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("End time must be after the current time.");
        }
        return new UpdateAuctionRequestMessage(
                currentAuctionId,
                title,
                description,
                startingPrice,
                increment,
                startTime,
                endTime
        );
    }

    private BigDecimal currentIncrement() {
        if (currentAuction.getMinimumNextBid() == null || currentAuction.getCurrentPrice() == null) {
            throw new IllegalArgumentException("Auction increment is not available.");
        }
        return currentAuction.getMinimumNextBid().subtract(currentAuction.getCurrentPrice());
    }

    private LocalDateTime parseDateTime(String raw, String fieldName) {
        String input = trim(raw);
        if (input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return LocalDateTime.parse(input, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must use yyyy-MM-dd HH:mm format.");
        }
    }

    private String deriveDisplayStatus(AuctionDetailsResponseMessage response) {
        if (response.getStatus() == AuctionStatus.CANCELED) {
            return "CANCELED";
        }
        if (response.getStatus() == AuctionStatus.PAID) {
            return "PAID";
        }
        if (response.getEndTime() != null && !LocalDateTime.now().isBefore(response.getEndTime())) {
            return hasWinner(response) ? "PAID" : "CANCELED";
        }
        return "RUNNING";
    }

    private boolean hasWinner(AuctionDetailsResponseMessage response) {
        String winner = response.getWinnerBidderId();
        String leadingBidder = response.getLeadingBidderId();
        return (winner != null && !winner.isBlank())
                || (leadingBidder != null && !leadingBidder.isBlank());
    }

    private boolean hasBids(AuctionDetailsResponseMessage response) {
        return response != null && !response.getBidHistory().isEmpty();
    }

    private boolean isRunning(AuctionDetailsResponseMessage response) {
        return response != null
                && response.getStatus() == AuctionStatus.RUNNING
                && response.getEndTime() != null
                && LocalDateTime.now().isBefore(response.getEndTime());
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void disableActions() {
        titleField.setDisable(true);
        descriptionField.setDisable(true);
        endTimeField.setDisable(true);
        saveButton.setDisable(true);
        cancelButton.setDisable(true);
        closeButton.setDisable(true);
    }

    private void setFormEditable(boolean editable) {
        titleField.setDisable(!editable);
        descriptionField.setDisable(!editable);
        endTimeField.setDisable(!editable);
    }

    private void configureActions(AuctionDetailsResponseMessage response, boolean admin) {
        boolean running = isRunning(response);
        boolean hasBids = hasBids(response);
        setButtonVisible(saveButton, true);
        setButtonVisible(cancelButton, true);
        setButtonVisible(closeButton, admin);

        titleField.setDisable(!running);
        descriptionField.setDisable(!running);
        endTimeField.setDisable(!running);
        saveButton.setDisable(actionRequestPending || !running);
        closeButton.setDisable(actionRequestPending || !admin || !running);
        cancelButton.setDisable(actionRequestPending || !running || (!admin && hasBids));
    }

    private void setActionButtonsVisible(boolean visible) {
        setButtonVisible(saveButton, visible);
        setButtonVisible(cancelButton, visible);
        setButtonVisible(closeButton, visible);
    }

    private void setButtonVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
        button.setDisable(!visible);
    }

    private void setActionRequestPending(boolean pending) {
        actionRequestPending = pending;
        if (currentAuction != null) {
            configureActions(currentAuction, ClientSession.getInstance().isAdmin());
        }
    }

    private boolean confirmAction(String title, String text) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(title);
        confirmation.setHeaderText(title);
        confirmation.setContentText(text);
        if (statusLabel.getScene() != null) {
            confirmation.initOwner(statusLabel.getScene().getWindow());
        }
        Optional<ButtonType> answer = confirmation.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    private void handleAuctionUpdated(AuctionUpdatedResponseMessage update) {
        if (update != null && currentAuctionId.equals(update.getAuctionId())) {
            requestAuctionDetails();
        }
    }

    private void handlePriceUpdate(PriceUpdateResponseMessage update) {
        if (update != null && currentAuctionId.equals(update.getAuctionId())) {
            requestAuctionDetails();
        }
    }

    private void handleAuctionEnded(AuctionEndedResponseMessage update) {
        if (update != null && currentAuctionId.equals(update.getAuctionId())) {
            requestAuctionDetails();
        }
    }

    private void setSuccessStatus(String text) {
        statusLabel.pseudoClassStateChanged(ERROR_STATE, false);
        statusLabel.pseudoClassStateChanged(SUCCESS_STATE, true);
        updateStatusText(text);
    }

    private void setErrorStatus(String text) {
        statusLabel.pseudoClassStateChanged(ERROR_STATE, true);
        statusLabel.pseudoClassStateChanged(SUCCESS_STATE, false);
        updateStatusText(text);
    }

    private void setInfoStatus(String text) {
        statusLabel.pseudoClassStateChanged(ERROR_STATE, false);
        statusLabel.pseudoClassStateChanged(SUCCESS_STATE, false);
        updateStatusText(text);
    }

    private void updateStatusText(String text) {
        boolean visible = text != null && !text.isBlank();
        statusLabel.setManaged(visible);
        statusLabel.setVisible(visible);
        statusLabel.setText(visible ? text : "");
    }
}
