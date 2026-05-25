package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class AuctionDetailsListResponseMessage extends Message {
    private List<AuctionDetailsResponseMessage> auctions;

    public AuctionDetailsListResponseMessage() {
        super(MessageType.AUCTION_DETAILS_LIST_RESPONSE);
    }

    public AuctionDetailsListResponseMessage(List<AuctionDetailsResponseMessage> auctions) {
        super(MessageType.AUCTION_DETAILS_LIST_RESPONSE);
        this.auctions = auctions == null ? List.of() : List.copyOf(auctions);
    }

    public List<AuctionDetailsResponseMessage> getAuctions() {
        return auctions == null ? List.of() : List.copyOf(auctions);
    }
}
