package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetAuctionDetailsRequestMessage extends Message {
    private String auctionId;

    public GetAuctionDetailsRequestMessage() {
        super(MessageType.GET_AUCTION_DETAILS_REQUEST);
    }

    public GetAuctionDetailsRequestMessage(String auctionId) {
        super(MessageType.GET_AUCTION_DETAILS_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }
}
