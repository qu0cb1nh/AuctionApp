package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AuctionActionResultMessage extends Message {
    private String message;

    public AuctionActionResultMessage() {
        super(MessageType.AUCTION_ACTION_SUCCESS);
    }

    public AuctionActionResultMessage(String message) {
        super(MessageType.AUCTION_ACTION_SUCCESS);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
