package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.notification.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.notification.GetNotificationsRequestMessage;

public final class NotificationService {
    private static final NotificationService INSTANCE = new NotificationService();

    public static NotificationService getInstance() {
        return INSTANCE;
    }

    private NotificationService() {
    }

    public void requestNotifications(MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetNotificationsRequestMessage(), callback);
    }

    public void clearNotification(String notificationId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new ClearNotificationsRequestMessage(notificationId), callback);
    }
}
