package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AuctionActionResponseMessage extends Message {
    private String message;

    public AuctionActionResponseMessage() {
        super(MessageType.AUCTION_ACTION_SUCCESS);
    }

    public AuctionActionResponseMessage(String message) {
        super(MessageType.AUCTION_ACTION_SUCCESS);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
