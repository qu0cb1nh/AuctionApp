package net.auctionapp.common.messages.auction;

import net.auctionapp.common.dto.AuctionSummaryDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class AuctionListResponseMessage extends Message {
    private List<AuctionSummaryDto> auctions;

    public AuctionListResponseMessage() {
        super(MessageType.AUCTION_LIST_RESPONSE);
    }

    public AuctionListResponseMessage(List<AuctionSummaryDto> auctions) {
        super(MessageType.AUCTION_LIST_RESPONSE);
        this.auctions = auctions == null ? List.of() : List.copyOf(auctions);
    }

    public List<AuctionSummaryDto> getAuctions() {
        return auctions == null ? List.of() : List.copyOf(auctions);
    }
}
