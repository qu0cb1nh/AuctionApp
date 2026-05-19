package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class CancelAuctionRequestMessage extends Message {
    private String auctionId;

    public CancelAuctionRequestMessage() {
        super(MessageType.CANCEL_AUCTION_REQUEST);
    }

    public CancelAuctionRequestMessage(String auctionId) {
        super(MessageType.CANCEL_AUCTION_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
