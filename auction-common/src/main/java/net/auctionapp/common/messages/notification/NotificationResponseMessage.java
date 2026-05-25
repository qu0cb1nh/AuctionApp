package net.auctionapp.common.messages.notification;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.notifications.Notification;

public class NotificationResponseMessage extends Message {
    private String recipientUserId;
    private Notification notification;

    public NotificationResponseMessage() {
        super(MessageType.NOTIFICATION);
    }

    public NotificationResponseMessage(String recipientUserId, Notification notification) {
        super(MessageType.NOTIFICATION);
        this.recipientUserId = recipientUserId;
        this.notification = notification;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public Notification getNotification() {
        return notification;
    }
}
