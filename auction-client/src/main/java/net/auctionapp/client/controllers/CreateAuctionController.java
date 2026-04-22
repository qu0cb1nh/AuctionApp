package net.auctionapp.client.controllers;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.models.items.ItemType;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class CreateAuctionController implements Initializable {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private TextField itemNameField;
    @FXML
    private ComboBox<ItemType> categoryComboBox;
    @FXML
    private TextField startingPriceField;
    @FXML
    private TextField incrementField;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private TextField endTimeField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private Label statusLabel;

    private Consumer<Message> detailsListener;
    private Consumer<Message> errorListener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Create Auction", true, "views/MainMenu.fxml");

        categoryComboBox.setItems(FXCollections.observableArrayList(ItemType.values()));
        categoryComboBox.getSelectionModel().select(ItemType.ELECTRONICS);

        LocalDate now = LocalDate.now();
        endDatePicker.setValue(now.plusDays(1));
        endTimeField.setText("21:00");

        detailsListener = this::handleCreateSuccess;
        errorListener = this::handleCreateError;
        ClientApp.getInstance().addMessageHandler(MessageType.AUCTION_DETAILS_RESPONSE, detailsListener);
        ClientApp.getInstance().addMessageHandler(MessageType.ERROR, errorListener);
    }

    @FXML
    public void handleBack(ActionEvent event) {
        cleanupHandlers();
        SceneNavigator.switchScene("views/MainMenu.fxml");
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        cleanupHandlers();
        SceneNavigator.switchScene("views/MainMenu.fxml");
    }

    @FXML
    public void handleCreateAuction(ActionEvent event) {
        try {
            CreateItemRequestMessage request = buildRequestFromForm();
            statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
            statusLabel.setText("Creating auction...");
            ClientApp.getInstance().getNetworkService().sendMessage(request);
        } catch (IllegalArgumentException ex) {
            statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #d9534f;");
            statusLabel.setText(ex.getMessage());
        }
    }

    private CreateItemRequestMessage buildRequestFromForm() {
        String title = safeTrim(itemNameField.getText());
        String description = safeTrim(descriptionArea.getText());
        String startingPriceText = safeTrim(startingPriceField.getText());
        String incrementText = safeTrim(incrementField.getText());

        if (title.isEmpty()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }
        if (categoryComboBox.getValue() == null) {
            throw new IllegalArgumentException("Category is required.");
        }

        BigDecimal startingPrice = parsePositiveDecimal(startingPriceText, "Starting price");
        BigDecimal increment = parsePositiveDecimal(incrementText, "Increment");

        LocalDateTime startDateTime = LocalDateTime.now();

        LocalDate endDate = endDatePicker.getValue();
        if (endDate == null) {
            throw new IllegalArgumentException("End date is required.");
        }

        LocalTime endTime = parseTime(safeTrim(endTimeField.getText()), "End time");

        LocalDateTime endDateTime = LocalDateTime.of(endDate, endTime);
        if (!endDateTime.isAfter(startDateTime)) {
            throw new IllegalArgumentException("End date/time must be after current time.");
        }

        CreateItemRequestMessage request = new CreateItemRequestMessage();
        request.setItemType(categoryComboBox.getValue());
        request.setTitle(title);
        request.setDescription(description);
        request.setBasePrice(startingPrice);
        request.setStartingPrice(startingPrice);
        request.setMinimumBidIncrement(increment);
        request.setStartTime(startDateTime);
        request.setEndTime(endDateTime);
        applyDefaultTypeSpecificFields(request);
        return request;
    }

    private void applyDefaultTypeSpecificFields(CreateItemRequestMessage request) {
        switch (request.getItemType()) {
            case ELECTRONICS -> {
                request.setBrand("Unknown");
                request.setModel("Unknown");
                request.setWarrantyMonths(0);
            }
            case ART -> {
                request.setAuthor("Unknown");
                request.setYearCreated(LocalDate.now().getYear());
            }
            case VEHICLE -> {
                request.setBrand("Unknown");
                request.setModel("Unknown");
                request.setYearCreated(LocalDate.now().getYear());
            }
        }
    }

    private void handleCreateSuccess(Message message) {
        if (!(message instanceof AuctionDetailsResponseMessage response)) {
            return;
        }
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2e7d32;");
        statusLabel.setText("Auction created: " + response.getTitle());
        cleanupHandlers();
        SceneNavigator.switchSceneWithDelay("views/AuctionList.fxml", 600);
    }

    private void handleCreateError(Message message) {
        if (!(message instanceof ErrorMessage errorMessage)) {
            return;
        }
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #d9534f;");
        statusLabel.setText(errorMessage.getErrorMessage());
    }

    private BigDecimal parsePositiveDecimal(String input, String fieldName) {
        if (input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            BigDecimal value = new BigDecimal(input);
            if (value.signum() <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than zero.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    private LocalTime parseTime(String input, String fieldName) {
        if (input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return LocalTime.parse(input, TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + " must use HH:mm format.");
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void cleanupHandlers() {
        if (detailsListener != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.AUCTION_DETAILS_RESPONSE, detailsListener);
        }
        if (errorListener != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.ERROR, errorListener);
        }
    }
}
