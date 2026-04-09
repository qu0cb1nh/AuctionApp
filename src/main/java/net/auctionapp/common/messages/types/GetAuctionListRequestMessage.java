package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetAuctionListRequestMessage extends Message {
    public GetAuctionListRequestMessage() {
        super(MessageType.GET_AUCTION_LIST_REQUEST);
    }
}
