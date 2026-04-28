package net.auctionapp.client.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientApp;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetNotificationsRequestMessage;
import net.auctionapp.common.messages.types.MarkNotificationReadRequestMessage;
import net.auctionapp.common.messages.types.NotificationMessage;
import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.common.messages.types.NotificationsResponseMessage;
import net.auctionapp.common.notifications.Notification;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class NotificationsController implements Initializable {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String LABEL_ACTIVE =
            "-fx-text-fill: #3bb3d1; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String LABEL_INACTIVE =
            "-fx-text-fill: #6b7280; -fx-font-weight: normal; -fx-cursor: hand;";
    private static final String UNDERLINE_ACTIVE =
            "-fx-background-color: #2f80ed; -fx-pref-height: 2;";
    private static final String UNDERLINE_INACTIVE =
            "-fx-background-color: transparent; -fx-pref-height: 2;";
    private static final String ACTION_BUTTON_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: #6b7280; -fx-font-weight: bold; -fx-cursor: hand;";

    @FXML
    private HeaderController appHeaderController;

    @FXML
    private VBox notificationCardsContainer;

    @FXML
    private Label filterStatusLabel;

    @FXML
    private Label filterLabelAll;
    @FXML
    private Label filterLabelBids;
    @FXML
    private Label filterLabelMyAuctions;
    @FXML
    private Label filterLabelSystem;
    @FXML
    private Label filterLabelResults;

    @FXML
    private Region filterUnderlineAll;
    @FXML
    private Region filterUnderlineBids;
    @FXML
    private Region filterUnderlineMyAuctions;
    @FXML
    private Region filterUnderlineSystem;
    @FXML
    private Region filterUnderlineResults;

    private Label[] filterLabels;
    private Region[] filterUnderlines;
    private int activeFilterIndex;

    private final List<Notification> allNotifications = new ArrayList<>();
    private Consumer<Message> notificationPushHandler;
    private boolean pushHandlerRegistered;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Notifications", true, "MainMenu");

        filterLabels = new Label[]{
                filterLabelAll, filterLabelBids, filterLabelMyAuctions, filterLabelSystem, filterLabelResults
        };
        filterUnderlines = new Region[]{
                filterUnderlineAll, filterUnderlineBids, filterUnderlineMyAuctions, filterUnderlineSystem,
                filterUnderlineResults
        };
        registerMessageHandlers();
        if (notificationCardsContainer != null) {
            notificationCardsContainer.sceneProperty().addListener((observable, oldScene, newScene) -> {
                if (oldScene != null) {
                    cleanupMessageHandlers();
                }
            });
        }
        setActiveFilter(0, "All");
        requestNotifications();
    }

    @FXML
    public void handleFilterAll(MouseEvent event) {
        setActiveFilter(0, "All");
    }

    @FXML
    public void handleFilterBids(MouseEvent event) {
        setActiveFilter(1, "Bids");
    }

    @FXML
    public void handleFilterMyAuctions(MouseEvent event) {
        setActiveFilter(2, "My Auctions");
    }

    @FXML
    public void handleFilterSystem(MouseEvent event) {
        setActiveFilter(3, "System");
    }

    @FXML
    public void handleFilterResults(MouseEvent event) {
        setActiveFilter(4, "Results");
    }

    private void setActiveFilter(int index, String nameForStatus) {
        if (filterLabels == null || filterUnderlines == null) {
            return;
        }
        activeFilterIndex = index;
        for (int i = 0; i < filterLabels.length; i++) {
            boolean active = i == index;
            filterLabels[i].setStyle(active ? LABEL_ACTIVE : LABEL_INACTIVE);
            filterUnderlines[i].setStyle(active ? UNDERLINE_ACTIVE : UNDERLINE_INACTIVE);
        }
        if (filterStatusLabel != null) {
            filterStatusLabel.setText("Showing: " + nameForStatus);
        }
        renderNotifications();
    }

    private void registerMessageHandlers() {
        notificationPushHandler = this::handleNotificationPush;
        ClientApp.getInstance().addMessageHandler(MessageType.NOTIFICATION, notificationPushHandler);
        pushHandlerRegistered = true;
    }

    private void cleanupMessageHandlers() {
        if (!pushHandlerRegistered) {
            return;
        }
        if (notificationPushHandler != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.NOTIFICATION, notificationPushHandler);
        }
        pushHandlerRegistered = false;
    }

    private void requestNotifications() {
        ClientApp.getInstance().sendRequest(new GetNotificationsRequestMessage(), this::handleNotificationsResponse);
    }

    private void handleNotificationsResponse(Message message, Throwable throwable) {
        if (throwable != null) {
            setStatusText("Failed to load notifications: " + throwable.getMessage(), true);
            return;
        }
        if (message instanceof ErrorMessage errorMessage) {
            setStatusText(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof NotificationsResponseMessage response)) {
            setStatusText("Unexpected response from server.", true);
            return;
        }
        updateNotifications(response.getNotifications());
    }

    private void handleNotificationPush(Message message) {
        if (!(message instanceof NotificationMessage notificationMessage)) {
            return;
        }
        Notification pushedNotification = notificationMessage.getNotification();
        if (pushedNotification == null) {
            return;
        }
        allNotifications.removeIf(existing -> matchesNotificationId(existing, pushedNotification.getId()));
        allNotifications.addFirst(pushedNotification);
        renderNotifications();
    }

    private void updateNotifications(List<Notification> notifications) {
        allNotifications.clear();
        if (notifications == null) {
            renderNotifications();
            return;
        }
        allNotifications.addAll(
                notifications.stream()
                        .filter(notification -> notification != null && notification.getId() != null)
                        .sorted(Comparator.comparing(
                                NotificationsController::notificationTimestampOrMin,
                                Comparator.reverseOrder()
                        ))
                        .toList()
        );
        renderNotifications();
    }

    private void renderNotifications() {
        if (notificationCardsContainer == null) {
            return;
        }
        notificationCardsContainer.getChildren().clear();

        List<Notification> filteredNotifications = allNotifications.stream()
                .filter(this::matchesActiveFilter)
                .toList();

        if (filteredNotifications.isEmpty()) {
            Label emptyLabel = new Label("No notifications yet.");
            emptyLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");
            notificationCardsContainer.getChildren().add(emptyLabel);
            return;
        }
        for (Notification notification : filteredNotifications) {
            notificationCardsContainer.getChildren().add(createNotificationCard(notification));
        }
    }

    private HBox createNotificationCard(Notification notification) {
        Label typeLabel = new Label(notificationTypeLabel(notification));
        typeLabel.setStyle("-fx-text-fill: #3bb3d1; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label timeLabel = new Label(formatTimestamp(notification.getCreatedAt()));
        timeLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11px;");

        Label titleLabel = new Label(safeText(notification.getTitle(), "Notification"));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        titleLabel.setWrapText(true);

        Label bodyLabel = new Label(safeText(notification.getBody(), ""));
        bodyLabel.setStyle("-fx-text-fill: #1f2937;");
        bodyLabel.setWrapText(true);

        MenuItem markReadItem = new MenuItem(notification.isRead() ? "Read" : "Mark as read");
        markReadItem.setDisable(notification.isRead());
        markReadItem.setOnAction(event -> markAsRead(notification.getId()));
        MenuItem clearItem = new MenuItem("Clear");
        clearItem.setOnAction(event -> clearNotification(notification.getId()));

        MenuButton actionMenuButton = new MenuButton("...");
        actionMenuButton.setStyle(ACTION_BUTTON_STYLE);
        actionMenuButton.getItems().setAll(markReadItem, clearItem);
        actionMenuButton.setVisible(false);
        actionMenuButton.setManaged(false);

        HBox titleRow = new HBox(8.0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        titleRow.getChildren().addAll(typeLabel, spacer, timeLabel, actionMenuButton);

        VBox content = new VBox(6.0, titleRow, titleLabel, bodyLabel);

        HBox card = new HBox(12.0, content);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 14;");
        card.setOnMouseEntered(event -> {
            actionMenuButton.setVisible(true);
            actionMenuButton.setManaged(true);
        });
        card.setOnMouseExited(event -> {
            actionMenuButton.hide();
            actionMenuButton.setVisible(false);
            actionMenuButton.setManaged(false);
        });
        return card;
    }

    private void markAsRead(String notificationId) {
        ClientApp.getInstance().sendRequest(
                new MarkNotificationReadRequestMessage(notificationId),
                this::handleNotificationsResponse
        );
    }

    private void clearNotification(String notificationId) {
        ClientApp.getInstance().sendRequest(
                new ClearNotificationsRequestMessage(notificationId),
                this::handleNotificationsResponse
        );
    }

    private boolean matchesActiveFilter(Notification notification) {
        if (notification == null) {
            return false;
        }
        if (activeFilterIndex == 0) {
            return true;
        }
        NotificationType notificationType = notification.getType();
        return switch (activeFilterIndex) {
            case 1 -> notificationType == NotificationType.OUTBID;
            case 2, 3, 4 -> false;
            default -> true;
        };
    }

    private String notificationTypeLabel(Notification notification) {
        if (notification == null || notification.getType() == null) {
            return "Notification";
        }
        return switch (notification.getType()) {
            case OUTBID -> "Bids";
        };
    }

    private String formatTimestamp(LocalDateTime createdAt) {
        if (createdAt == null) {
            return "Unknown time";
        }
        return CARD_TIME_FORMATTER.format(createdAt);
    }

    private static LocalDateTime notificationTimestampOrMin(Notification notification) {
        if (notification == null || notification.getCreatedAt() == null) {
            return LocalDateTime.MIN;
        }
        return notification.getCreatedAt();
    }

    private static boolean matchesNotificationId(Notification notification, String notificationId) {
        if (notification == null || notification.getId() == null || notificationId == null) {
            return false;
        }
        return notification.getId().equals(notificationId);
    }

    private void setStatusText(String text, boolean isError) {
        if (filterStatusLabel == null) {
            return;
        }
        filterStatusLabel.setText(text);
        filterStatusLabel.setStyle(isError
                ? "-fx-text-fill: #d9534f; -fx-font-size: 12;"
                : "-fx-text-fill: #6b7280; -fx-font-size: 12;");
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

}
