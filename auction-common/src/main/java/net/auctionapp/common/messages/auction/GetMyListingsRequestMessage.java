package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetMyListingsRequestMessage extends Message {
    public GetMyListingsRequestMessage() {
        super(MessageType.GET_MY_LISTINGS_REQUEST);
    }
}
