package net.auctionapp.common.messages.notification;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.notifications.Notification;

public class NotificationResponseMessage extends Message {
    private Notification notification;

    public NotificationResponseMessage() {
        super(MessageType.NOTIFICATION);
    }

    public NotificationResponseMessage(Notification notification) {
        super(MessageType.NOTIFICATION);
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }
}
