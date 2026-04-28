package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class ClearNotificationsRequestMessage extends Message {
    private String notificationId;

    public ClearNotificationsRequestMessage() {
        super(MessageType.CLEAR_NOTIFICATIONS_REQUEST);
    }

    public ClearNotificationsRequestMessage(String notificationId) {
        super(MessageType.CLEAR_NOTIFICATIONS_REQUEST);
        this.notificationId = notificationId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }
}
