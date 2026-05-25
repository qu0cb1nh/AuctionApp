package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class AuctionEndedResponseMessage extends Message {
    private String auctionId;
    private String winnerBidderId;
    private BigDecimal finalPrice;

    public AuctionEndedResponseMessage() {
        super(MessageType.AUCTION_ENDED);
    }

    public AuctionEndedResponseMessage(String auctionId, String winnerBidderId, BigDecimal finalPrice) {
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
