package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class CloseAuctionRequestMessage extends Message {
    private String auctionId;

    public CloseAuctionRequestMessage() {
        super(MessageType.CLOSE_AUCTION_REQUEST);
    }

    public CloseAuctionRequestMessage(String auctionId) {
        super(MessageType.CLOSE_AUCTION_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
