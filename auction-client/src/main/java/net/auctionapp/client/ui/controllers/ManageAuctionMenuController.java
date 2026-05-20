package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AuctionActionResultMessage;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.UpdateAuctionRequestMessage;
import net.auctionapp.common.auction.AuctionStatus;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;

public class ManageAuctionMenuController implements Initializable {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
    private TextField startingPriceField;
    @FXML
    private TextField incrementField;
    @FXML
    private TextField startTimeField;
    @FXML
    private TextField endTimeField;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button closeButton;

    private String currentAuctionId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Manage Auction", true, "AuctionListMenu.fxml");
        currentAuctionId = ClientApp.getInstance().getSelectedAuctionId();
        if (currentAuctionId == null || currentAuctionId.isBlank()) {
            setErrorStatus("No auction selected.");
            disableActions();
            return;
        }
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
            AuctionService.getInstance().updateAuction(request, this::handleActionResponse);
        } catch (IllegalArgumentException e) {
            setErrorStatus(e.getMessage());
        }
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        AuctionService.getInstance().cancelAuction(currentAuctionId, this::handleActionResponse);
    }

    @FXML
    public void handleClose(ActionEvent event) {
        AuctionService.getInstance().closeAuction(currentAuctionId, this::handleActionResponse);
    }

    private void requestAuctionDetails() {
        setInfoStatus("Loading auction details...");
        AuctionService.getInstance().requestAuctionDetails(currentAuctionId, this::handleAuctionDetailsResponse);
    }

    private void handleAuctionDetailsResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionDetailsResponseMessage response)) {
            setErrorStatus("Unexpected response from server.");
            return;
        }

        auctionInfoLabel.setText("Managing: " + response.getTitle() + " (" + deriveDisplayStatus(response) + ")");
        titleField.setText(response.getTitle());
        descriptionField.setText(response.getDescription());
        startingPriceField.setText(response.getStartingPrice() == null ? "" : response.getStartingPrice().stripTrailingZeros().toPlainString());
        incrementField.setText(response.getMinimumNextBid() == null || response.getCurrentPrice() == null
                ? ""
                : response.getMinimumNextBid().subtract(response.getCurrentPrice()).stripTrailingZeros().toPlainString());
        startTimeField.setText(formatDateTime(response.getStartTime()));
        endTimeField.setText(formatDateTime(response.getEndTime()));
        boolean canManageAuction = ClientSession.getInstance().canManageAuction(response.getSellerId());
        setActionButtonsVisible(canManageAuction);
        setFormEditable(canManageAuction);
        boolean canChangeRunningAuction = canManageAuction && response.getStatus() == AuctionStatus.RUNNING;
        saveButton.setDisable(!canChangeRunningAuction);
        closeButton.setDisable(!canChangeRunningAuction);
        cancelButton.setDisable(!canChangeRunningAuction);
        if (!canManageAuction) {
            setErrorStatus("Only the seller or an admin can manage this auction.");
            return;
        }
        setSuccessStatus("Auction details loaded.");
    }

    private void handleActionResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionActionResultMessage result)) {
            setErrorStatus("Unexpected response from server.");
            return;
        }
        setSuccessStatus(result.getMessage());
        if (result.getMessage() != null && result.getMessage().toLowerCase().contains("canceled")) {
            SceneManager.switchSceneWithDelay("AuctionListMenu.fxml", 500);
            return;
        }
        requestAuctionDetails();
    }

    private UpdateAuctionRequestMessage buildUpdateRequest() {
        String title = trim(titleField.getText());
        String description = trim(descriptionField.getText());
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }
        BigDecimal startingPrice = parseDecimal(startingPriceField.getText(), "Starting price", false);
        BigDecimal increment = parseDecimal(incrementField.getText(), "Increment", true);
        LocalDateTime startTime = parseDateTime(startTimeField.getText(), "Start time");
        LocalDateTime endTime = parseDateTime(endTimeField.getText(), "End time");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
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

    private BigDecimal parseDecimal(String raw, String fieldName, boolean mustBePositive) {
        String input = trim(raw);
        if (input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            BigDecimal value = new BigDecimal(input);
            if (mustBePositive && value.signum() <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than zero.");
            }
            if (!mustBePositive && value.signum() < 0) {
                throw new IllegalArgumentException(fieldName + " must not be negative.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
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
        startingPriceField.setDisable(true);
        incrementField.setDisable(true);
        startTimeField.setDisable(true);
        endTimeField.setDisable(true);
        saveButton.setDisable(true);
        cancelButton.setDisable(true);
        closeButton.setDisable(true);
    }

    private void setFormEditable(boolean editable) {
        titleField.setDisable(!editable);
        descriptionField.setDisable(!editable);
        startingPriceField.setDisable(!editable);
        incrementField.setDisable(!editable);
        startTimeField.setDisable(!editable);
        endTimeField.setDisable(!editable);
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

    private void setSuccessStatus(String text) {
        statusLabel.setStyle("-fx-text-fill: #1f8f4c;");
        statusLabel.setText(text);
    }

    private void setErrorStatus(String text) {
        statusLabel.setStyle("-fx-text-fill: #c13c21;");
        statusLabel.setText(text);
    }

    private void setInfoStatus(String text) {
        statusLabel.setStyle("-fx-text-fill: #3f5569;");
        statusLabel.setText(text);
    }
}
