package net.auctionapp.common.messages.watchlist;

import net.auctionapp.common.dto.AuctionSummary;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class WatchListResponseMessage extends Message {
    private List<AuctionSummary> auctions;

    public WatchListResponseMessage() {
        super(MessageType.WATCH_LIST_RESPONSE);
    }

    public WatchListResponseMessage(List<AuctionSummary> auctions) {
        super(MessageType.WATCH_LIST_RESPONSE);
        this.auctions = auctions == null ? List.of() : List.copyOf(auctions);
    }

    public List<AuctionSummary> getAuctions() {
        return auctions == null ? List.of() : List.copyOf(auctions);
    }
}
