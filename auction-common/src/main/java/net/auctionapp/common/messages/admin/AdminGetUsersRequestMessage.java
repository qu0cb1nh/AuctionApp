package net.auctionapp.common.messages.admin;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AdminGetUsersRequestMessage extends Message {
    public AdminGetUsersRequestMessage() {
        super(MessageType.ADMIN_GET_USERS_REQUEST);
    }
}
