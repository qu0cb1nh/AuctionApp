package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AuctionUpdatedResponseMessage extends Message {
    private String auctionId;

    public AuctionUpdatedResponseMessage() {
        super(MessageType.AUCTION_UPDATED);
    }

    public AuctionUpdatedResponseMessage(String auctionId) {
        super(MessageType.AUCTION_UPDATED);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
