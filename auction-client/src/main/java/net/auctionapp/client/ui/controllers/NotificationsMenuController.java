package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.NotificationCardController;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.auctionapp.client.services.NotificationService;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.ResourcesUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.notification.NotificationResponseMessage;
import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.common.messages.notification.NotificationsResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.notifications.Notification;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationsMenuController implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationsMenuController.class);
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String LABEL_ACTIVE =
            "-fx-text-fill: #3bb3d1; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String LABEL_INACTIVE =
            "-fx-text-fill: #6b7280; -fx-font-weight: normal; -fx-cursor: hand;";
    private static final String UNDERLINE_ACTIVE =
            "-fx-background-color: #2f80ed; -fx-pref-height: 2;";
    private static final String UNDERLINE_INACTIVE =
            "-fx-background-color: transparent; -fx-pref-height: 2;";
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Notifications");

        filterLabels = new Label[]{
                filterLabelAll, filterLabelBids, filterLabelMyAuctions, filterLabelSystem, filterLabelResults
        };
        filterUnderlines = new Region[]{
                filterUnderlineAll, filterUnderlineBids, filterUnderlineMyAuctions, filterUnderlineSystem,
                filterUnderlineResults
        };
        registerMessageListeners();
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

    private void registerMessageListeners() {
        SceneManager.registerSceneMessageListener(MessageType.NOTIFICATION, this::handleNotificationPush);
    }

    private void requestNotifications() {
        NotificationService.getInstance().requestNotifications(this::handleNotificationsResponse);
    }

    private void handleNotificationsResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            setStatusText(errorMessage.getErrorMessage(), true);
            return;
        }
        if (!(message instanceof NotificationsResponseMessage response)) {
            setStatusText("Unexpected response from server.", true);
            return;
        }
        updateNotifications(response.getNotifications());
    }

    private void handleNotificationPush(NotificationResponseMessage notificationMessage) {
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
                                NotificationsMenuController::notificationTimestampOrMin,
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
            notificationCardsContainer.getChildren().add(loadNotificationCard(notification));
        }
    }

    private Pane loadNotificationCard(Notification notification) {
        try {
            FXMLLoader loader = ResourcesUtil.fxmlLoader("components/NotificationCard.fxml");
            Pane card = loader.load();
            NotificationCardController controller = loader.getController();
            controller.bindNotification(
                    notification,
                    notificationTypeLabel(notification),
                    formatTimestamp(notification == null ? null : notification.getCreatedAt()),
                    "Notification",
                    ""
            );
            String notificationId = notification == null ? null : notification.getId();
            String auctionId = notification == null ? null : notification.getAuctionId();
            controller.setClearAction(() -> clearNotification(notificationId));
            controller.setOpenAuctionAction(auctionId == null || auctionId.isBlank()
                    ? null
                    : () -> SceneManager.switchToAuctionDetails(auctionId));
            return card;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to render notification card.", e);
            setStatusText("Failed to load one notification card.", true);
            return new Pane();
        }
    }

    private void clearNotification(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            return;
        }
        NotificationService.getInstance().clearNotification(notificationId, this::handleNotificationsResponse);
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
            case 2 -> notificationType == NotificationType.AUCTION_SELLER_RESULT;
            case 3 -> notificationType == NotificationType.WATCH_LIST_ENDING_SOON;
            case 4 -> notificationType == NotificationType.AUCTION_WON;
            default -> true;
        };
    }

    private String notificationTypeLabel(Notification notification) {
        if (notification == null || notification.getType() == null) {
            return "Notification";
        }
        return switch (notification.getType()) {
            case OUTBID -> "Bids";
            case AUCTION_WON -> "Results";
            case AUCTION_SELLER_RESULT -> "My Auctions";
            case WATCH_LIST_ENDING_SOON -> "System";
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
}
