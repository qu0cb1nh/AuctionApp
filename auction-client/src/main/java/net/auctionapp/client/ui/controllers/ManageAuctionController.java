package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.services.AdminService;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.services.AuthService;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AdminActionResultMessage;
import net.auctionapp.common.messages.types.AdminUpdateAuctionRequestMessage;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.models.auction.AuctionStatus;
import net.auctionapp.common.models.users.UserRole;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;

public class ManageAuctionController implements Initializable {
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
    private Button forceCloseButton;
    @FXML
    private Button resetButton;
    @FXML
    private Button deleteButton;

    private String currentAuctionId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Manage Auction", true, "AuctionList");
        if (AuthService.getInstance().getCurrentUserRole() != UserRole.ADMIN) {
            setErrorStatus("Admin privileges are required.");
            disableActions();
            return;
        }

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
            AdminUpdateAuctionRequestMessage request = buildUpdateRequest();
            AdminService.getInstance().updateAuction(request, this::handleActionResponse);
        } catch (IllegalArgumentException e) {
            setErrorStatus(e.getMessage());
        }
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        AdminService.getInstance().deleteAuction(currentAuctionId, this::handleActionResponse);
    }

    @FXML
    public void handleForceClose(ActionEvent event) {
        AdminService.getInstance().forceCloseAuction(currentAuctionId, this::handleActionResponse);
    }

    @FXML
    public void handleReset(ActionEvent event) {
        AdminService.getInstance().resetAuction(currentAuctionId, this::handleActionResponse);
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
        saveButton.setDisable(response.getStatus() != AuctionStatus.RUNNING);
        setSuccessStatus("Auction details loaded.");
    }

    private void handleActionResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AdminActionResultMessage result)) {
            setErrorStatus("Unexpected response from server.");
            return;
        }
        setSuccessStatus(result.getMessage());
        if (result.getMessage() != null && result.getMessage().toLowerCase().contains("deleted")) {
            SceneManager.switchSceneWithDelay("AuctionList", 500);
            return;
        }
        requestAuctionDetails();
    }

    private AdminUpdateAuctionRequestMessage buildUpdateRequest() {
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
        return new AdminUpdateAuctionRequestMessage(
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
        LocalDateTime now = LocalDateTime.now();
        if (response.getStartTime() != null && now.isBefore(response.getStartTime())) {
            return "OPEN";
        }
        if (response.getEndTime() != null && !now.isBefore(response.getEndTime())) {
            return "FINISHED";
        }
        return "RUNNING";
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
        forceCloseButton.setDisable(true);
        resetButton.setDisable(true);
        deleteButton.setDisable(true);
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
