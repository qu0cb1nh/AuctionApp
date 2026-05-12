package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AdminResetAuctionRequestMessage extends Message {
    private String auctionId;

    public AdminResetAuctionRequestMessage() {
        super(MessageType.ADMIN_RESET_AUCTION_REQUEST);
    }

    public AdminResetAuctionRequestMessage(String auctionId) {
        super(MessageType.ADMIN_RESET_AUCTION_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
