package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class ObserverAuctionMessage extends Message {
    private String auctionId;
    private boolean observing;

    public ObserverAuctionMessage() {
        super(MessageType.OBSERVE_AUCTION_REQUEST);
    }

    public ObserverAuctionMessage(String auctionId, boolean observing) {
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

    public void setObserving(boolean observing) {
        this.observing = observing;
    }
}
