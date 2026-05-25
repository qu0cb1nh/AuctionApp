package net.auctionapp.common.messages.notification;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetNotificationsRequestMessage extends Message {
    public GetNotificationsRequestMessage() {
        super(MessageType.GET_NOTIFICATIONS_REQUEST);
    }
}
