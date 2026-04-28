package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.notifications.NotificationView;

public class NotificationMessage extends Message {
    private String recipientUserId;
    private NotificationView notification;

    public NotificationMessage() {
        super(MessageType.NOTIFICATION);
    }

    public NotificationMessage(String recipientUserId, NotificationView notification) {
        super(MessageType.NOTIFICATION);
        this.recipientUserId = recipientUserId;
        this.notification = notification;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public NotificationView getNotification() {
        return notification;
    }
}
