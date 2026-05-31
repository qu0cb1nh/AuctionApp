package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class CancelBidsRequestMessage extends Message {
    private String auctionId;

    public CancelBidsRequestMessage() {
        super(MessageType.CANCEL_BIDS_REQUEST);
    }

    public CancelBidsRequestMessage(String auctionId) {
        super(MessageType.CANCEL_BIDS_REQUEST);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
