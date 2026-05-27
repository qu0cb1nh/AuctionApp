package net.auctionapp.common.messages.admin;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetUserListRequestMessage extends Message {
    public GetUserListRequestMessage() {
        super(MessageType.GET_USER_LIST_REQUEST);
    }
}
