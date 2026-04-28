package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class MarkNotificationReadRequestMessage extends Message {
    private String notificationId;

    public MarkNotificationReadRequestMessage() {
        super(MessageType.MARK_NOTIFICATION_READ_REQUEST);
    }

    public MarkNotificationReadRequestMessage(String notificationId) {
        super(MessageType.MARK_NOTIFICATION_READ_REQUEST);
        this.notificationId = notificationId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }
}
