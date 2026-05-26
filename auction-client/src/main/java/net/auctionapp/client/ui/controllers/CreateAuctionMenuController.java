package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.ResourceBundle;

public class CreateAuctionMenuController implements Initializable {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SUCCESS_STATE = PseudoClass.getPseudoClass("success");
    private static final PseudoClass SELECTED_STATE = PseudoClass.getPseudoClass("selected");

    private boolean imageUploaded;

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private TextField itemNameField;
    @FXML
    private ComboBox<ItemType> categoryComboBox;
    @FXML
    private VBox electronicsFieldsBox;
    @FXML
    private VBox artFieldsBox;
    @FXML
    private VBox vehicleFieldsBox;
    @FXML
    private TextField electronicsBrandField;
    @FXML
    private TextField electronicsModelField;
    @FXML
    private TextField electronicsWarrantyField;
    @FXML
    private TextField artAuthorField;
    @FXML
    private TextField artYearCreatedField;
    @FXML
    private TextField vehicleBrandField;
    @FXML
    private TextField vehicleModelField;
    @FXML
    private TextField vehicleYearCreatedField;
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
    @FXML
    private ImageView previewImageView;
    @FXML
    private Label imageStatusLabel;
    @FXML
    private Button createAuctionButton;

    private File selectedImageFile;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Create Auction");

        categoryComboBox.setItems(FXCollections.observableArrayList(ItemType.values()));
        categoryComboBox.getSelectionModel().select(ItemType.ELECTRONICS);
        categoryComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateTypeSpecificFields(newValue));

        LocalDate now = LocalDate.now();
        endDatePicker.setValue(now.plusDays(1));
        endTimeField.setText("21:00");
        artYearCreatedField.setText(String.valueOf(now.getYear()));
        vehicleYearCreatedField.setText(String.valueOf(now.getYear()));
        electronicsWarrantyField.setText("12");
        updateTypeSpecificFields(categoryComboBox.getValue());
        imageUploaded = false;
        setButtonVisible(createAuctionButton, ClientSession.getInstance().canCreateAuction());
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        SceneManager.goBackOrSwitchScene("DashboardMenu.fxml");
    }

    @FXML
    public void handleChooseImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose auction item image");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files",
                "*.jpg",
                "*.jpeg",
                "*.png",
                "*.gif",
                "*.webp"
        ));
        File chosenFile = fileChooser.showOpenDialog(itemNameField.getScene().getWindow());
        if (chosenFile == null) {
            return;
        }
        if (!chosenFile.isFile()) {
            setImageStatus("Selected path is not a file.", true);
            return;
        }
        if (chosenFile.length() > MAX_IMAGE_BYTES) {
            setImageStatus("Image must be 5 MB or smaller.", true);
            return;
        }
        selectedImageFile = chosenFile;
        previewImageView.setImage(new Image(chosenFile.toURI().toString(), true));
        imageUploaded = true;
        setImageStatus(chosenFile.getName(), false);
    }

    @FXML
    public void handleCreateAuction(ActionEvent event) {
        if (!ClientSession.getInstance().canCreateAuction()) {
            setStatus("Please log in before creating an auction.", ERROR_STATE);
            return;
        }
        try {
            CreateItemRequestMessage request = buildRequestFromForm();
            setStatus("Creating auction...", null);
            createAuctionButton.setDisable(true);
            AuctionService.getInstance().createAuction(request, this::handleCreateResponse);
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), ERROR_STATE);
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
        request.setStartingPrice(startingPrice);
        request.setMinimumBidIncrement(increment);
        request.setStartTime(startDateTime);
        request.setEndTime(endDateTime);
        applyTypeSpecificFields(request);
        applyImageFields(request);
        return request;
    }

    private void applyImageFields(CreateItemRequestMessage request) {
        if (selectedImageFile == null) {
            return;
        }
        try {
            String contentType = resolveImageContentType(selectedImageFile);
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                throw new IllegalArgumentException("Selected file must be an image.");
            }
            byte[] imageBytes = Files.readAllBytes(selectedImageFile.toPath());
            request.setImageContentType(contentType);
            request.setImageBase64(Base64.getEncoder().encodeToString(imageBytes));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read selected image.");
        }
    }

    private String resolveImageContentType(File imageFile) throws IOException {
        String contentType = Files.probeContentType(imageFile.toPath());
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        String fileName = imageFile.getName().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    private void applyTypeSpecificFields(CreateItemRequestMessage request) {
        switch (request.getItemType()) {
            case ELECTRONICS -> {
                request.setBrand(requireNonBlank(electronicsBrandField.getText(), "Electronics brand"));
                request.setModel(requireNonBlank(electronicsModelField.getText(), "Electronics model"));
                request.setWarrantyMonths(parseNonNegativeInt(
                        safeTrim(electronicsWarrantyField.getText()),
                        "Electronics warranty months"
                ));
            }
            case ART -> {
                request.setAuthor(requireNonBlank(artAuthorField.getText(), "Art author"));
                request.setYearCreated(parsePositiveInt(safeTrim(artYearCreatedField.getText()), "Art year created"));
            }
            case VEHICLE -> {
                request.setBrand(requireNonBlank(vehicleBrandField.getText(), "Vehicle brand"));
                request.setModel(requireNonBlank(vehicleModelField.getText(), "Vehicle model"));
                request.setYearCreated(parsePositiveInt(
                        safeTrim(vehicleYearCreatedField.getText()),
                        "Vehicle year created"
                ));
            }
        }
    }

    private void updateTypeSpecificFields(ItemType itemType) {
        setSectionVisible(electronicsFieldsBox, itemType == ItemType.ELECTRONICS);
        setSectionVisible(artFieldsBox, itemType == ItemType.ART);
        setSectionVisible(vehicleFieldsBox, itemType == ItemType.VEHICLE);
        if (!imageUploaded) {
            previewImageView.setImage(new Image(ResourcesUtil.itemPlaceholder(itemType).toExternalForm(), true));
        }
    }

    private void setSectionVisible(VBox section, boolean visible) {
        section.setVisible(visible);
        section.setManaged(visible);
    }

    private void setButtonVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
        button.setDisable(!visible);
    }

    private void handleCreateResponse(Message message) {
        createAuctionButton.setDisable(false);
        if (message instanceof ErrorResponseMessage errorMessage) {
            setStatus(errorMessage.getErrorMessage(), ERROR_STATE);
            return;
        }
        if (!(message instanceof CreateItemResponseMessage result) || message.getType() != MessageType.CREATE_ITEM_SUCCESS) {
            setStatus("Unexpected response from server.", ERROR_STATE);
            return;
        }
        String responseText = result.getImageUrl() == null || result.getImageUrl().isBlank()
                ? result.getMessage()
                : result.getMessage() + " Image uploaded.";
        setStatus(responseText, SUCCESS_STATE);
        NotificationToastManager.showSuccess(responseText);
        SceneManager.switchToAuctionDetails(result.getAuctionId());
    }

    private void setImageStatus(String text, boolean error) {
        imageStatusLabel.pseudoClassStateChanged(ERROR_STATE, error);
        imageStatusLabel.pseudoClassStateChanged(SELECTED_STATE, !error);
        imageStatusLabel.setText(text);
    }

    private void setStatus(String text, PseudoClass state) {
        statusLabel.pseudoClassStateChanged(ERROR_STATE, state == ERROR_STATE);
        statusLabel.pseudoClassStateChanged(SUCCESS_STATE, state == SUCCESS_STATE);
        statusLabel.setText(text);
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

    private String requireNonBlank(String value, String fieldName) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return trimmed;
    }

    private int parsePositiveInt(String input, String fieldName) {
        if (input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            int value = Integer.parseInt(input);
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than zero.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid integer.");
        }
    }

    private int parseNonNegativeInt(String input, String fieldName) {
        if (input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            int value = Integer.parseInt(input);
            if (value < 0) {
                throw new IllegalArgumentException(fieldName + " must not be negative.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid integer.");
        }
    }

}
