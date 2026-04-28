package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.notifications.Notification;

import java.util.List;

public class NotificationsResponseMessage extends Message {
    private List<Notification> notifications;

    public NotificationsResponseMessage() {
        super(MessageType.NOTIFICATIONS_RESPONSE);
    }

    public NotificationsResponseMessage(List<Notification> notifications) {
        super(MessageType.NOTIFICATIONS_RESPONSE);
        this.notifications = notifications;
    }

    public List<Notification> getNotifications() {
        return notifications == null ? List.of() : notifications;
    }
}
