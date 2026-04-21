package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class AuctionEndedMessage extends Message {
    private String auctionId;
    private String winnerBidderId;
    private BigDecimal finalPrice;

    public AuctionEndedMessage() {
        super(MessageType.AUCTION_ENDED);
    }

    public AuctionEndedMessage(String auctionId, String winnerBidderId, BigDecimal finalPrice) {
        super(MessageType.AUCTION_ENDED);
        this.auctionId = auctionId;
        this.winnerBidderId = winnerBidderId;
        this.finalPrice = finalPrice;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getWinnerBidderId() {
        return winnerBidderId;
    }

    public BigDecimal getFinalPrice() {
        return finalPrice;
    }
}
