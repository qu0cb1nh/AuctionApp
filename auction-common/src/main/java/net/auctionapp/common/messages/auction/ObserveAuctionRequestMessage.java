package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class ObserveAuctionRequestMessage extends Message {
    private String auctionId;
    private boolean observing;

    public ObserveAuctionRequestMessage() {
        super(MessageType.OBSERVE_AUCTION_REQUEST);
    }

    public ObserveAuctionRequestMessage(String auctionId, boolean observing) {
        super(MessageType.OBSERVE_AUCTION_REQUEST);
        this.auctionId = auctionId;
        this.observing = observing;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public boolean isObserving() {
        return observing;
    }

}
