package net.auctionapp.common.messages.watchlist;

import net.auctionapp.common.dto.AuctionSummaryDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class WatchListResponseMessage extends Message {
    private List<AuctionSummaryDto> auctions;

    public WatchListResponseMessage() {
        super(MessageType.WATCH_LIST_RESPONSE);
    }

    public WatchListResponseMessage(List<AuctionSummaryDto> auctions) {
        super(MessageType.WATCH_LIST_RESPONSE);
        this.auctions = auctions == null ? List.of() : List.copyOf(auctions);
    }

    public List<AuctionSummaryDto> getAuctions() {
        return auctions == null ? List.of() : List.copyOf(auctions);
    }
}
