package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.notifications.NotificationView;

import java.util.List;

public class NotificationsResponseMessage extends Message {
    private List<NotificationView> notifications;

    public NotificationsResponseMessage() {
        super(MessageType.NOTIFICATIONS_RESPONSE);
    }

    public NotificationsResponseMessage(List<NotificationView> notifications) {
        super(MessageType.NOTIFICATIONS_RESPONSE);
        this.notifications = notifications;
    }

    public List<NotificationView> getNotifications() {
        return notifications == null ? List.of() : notifications;
    }
}
