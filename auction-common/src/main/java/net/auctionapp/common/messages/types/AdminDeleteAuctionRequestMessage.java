package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AdminDeleteAuctionRequestMessage extends Message {
    private String auctionId;

    public AdminDeleteAuctionRequestMessage() {
        super(MessageType.ADMIN_DELETE_AUCTION_REQUEST);
    }

    public AdminDeleteAuctionRequestMessage(String auctionId) {
        super(MessageType.ADMIN_DELETE_AUCTION_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
