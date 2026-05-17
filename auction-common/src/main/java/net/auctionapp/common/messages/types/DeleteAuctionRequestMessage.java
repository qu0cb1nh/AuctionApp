package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class DeleteAuctionRequestMessage extends Message {
    private String auctionId;

    public DeleteAuctionRequestMessage() {
        super(MessageType.DELETE_AUCTION_REQUEST);
    }

    public DeleteAuctionRequestMessage(String auctionId) {
        super(MessageType.DELETE_AUCTION_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
