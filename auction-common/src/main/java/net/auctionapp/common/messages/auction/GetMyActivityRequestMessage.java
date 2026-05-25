package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetMyActivityRequestMessage extends Message {
    public GetMyActivityRequestMessage() {
        super(MessageType.GET_MY_ACTIVITY_REQUEST);
    }
}
