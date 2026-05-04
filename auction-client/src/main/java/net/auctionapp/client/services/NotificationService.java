package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.types.GetNotificationsRequestMessage;
import net.auctionapp.common.messages.types.MarkNotificationReadRequestMessage;
import net.auctionapp.common.messages.types.NotificationMessage;

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

    public void markAsRead(String notificationId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new MarkNotificationReadRequestMessage(notificationId), callback);
    }

    public void clearNotification(String notificationId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new ClearNotificationsRequestMessage(notificationId), callback);
    }

    public void addNotificationListener(MessageListener<NotificationMessage> listener) {
        NetworkService.getInstance().addMessageListener(MessageType.NOTIFICATION, listener);
    }

    public void removeNotificationListener(MessageListener<NotificationMessage> listener) {
        NetworkService.getInstance().removeMessageListener(MessageType.NOTIFICATION, listener);
    }
}

