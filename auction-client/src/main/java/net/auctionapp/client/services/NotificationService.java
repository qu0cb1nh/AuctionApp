package net.auctionapp.client.services;

import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.notification.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.notification.GetNotificationsRequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private static final NotificationService INSTANCE = new NotificationService();

    public static NotificationService getInstance() {
        return INSTANCE;
    }

    private NotificationService() {
    }

    public void requestNotifications(MessageListener<Message> callback) {
        LOGGER.debug("Requesting notifications.");
        NetworkService.getInstance().sendRequest(new GetNotificationsRequestMessage(), callback);
    }

    public void clearNotification(String notificationId, MessageListener<Message> callback) {
        LOGGER.info("Submitting clear-notification request for notification {}.", notificationId);
        NetworkService.getInstance().sendRequest(new ClearNotificationsRequestMessage(notificationId), callback);
    }
}
