package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class AuctionListResponseMessage extends Message {
    private List<AuctionSummary> auctions;

    public AuctionListResponseMessage() {
        super(MessageType.AUCTION_LIST_RESPONSE);
    }

    public AuctionListResponseMessage(List<AuctionSummary> auctions) {
        super(MessageType.AUCTION_LIST_RESPONSE);
        this.auctions = auctions;
    }

    public List<AuctionSummary> getAuctions() {
        return auctions;
    }
}
