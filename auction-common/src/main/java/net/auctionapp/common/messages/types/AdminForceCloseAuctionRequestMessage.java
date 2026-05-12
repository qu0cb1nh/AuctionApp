package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AdminForceCloseAuctionRequestMessage extends Message {
    private String auctionId;

    public AdminForceCloseAuctionRequestMessage() {
        super(MessageType.ADMIN_FORCE_CLOSE_AUCTION_REQUEST);
    }

    public AdminForceCloseAuctionRequestMessage(String auctionId) {
        super(MessageType.ADMIN_FORCE_CLOSE_AUCTION_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
