package net.auctionapp.common.messages.notification;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class ClearNotificationsRequestMessage extends Message {
    private String notificationId;
    private boolean clearAll;

    public ClearNotificationsRequestMessage() {
        super(MessageType.CLEAR_NOTIFICATIONS_REQUEST);
    }

    public ClearNotificationsRequestMessage(String notificationId) {
        super(MessageType.CLEAR_NOTIFICATIONS_REQUEST);
        this.notificationId = notificationId;
    }

    public ClearNotificationsRequestMessage(boolean clearAll) {
        super(MessageType.CLEAR_NOTIFICATIONS_REQUEST);
        this.clearAll = clearAll;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public boolean isClearAll() {
        return clearAll;
    }

    public void setClearAll(boolean clearAll) {
        this.clearAll = clearAll;
    }
}
