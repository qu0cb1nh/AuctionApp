package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class BidResultMessage extends Message {
    private String auctionId;
    private BigDecimal currentPrice;
    private String leadingBidderId;
    private String message;

    public BidResultMessage() {
        super();
    }

    public BidResultMessage(MessageType type, String auctionId, BigDecimal currentPrice, String leadingBidderId, String message) {
        super(type);
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.leadingBidderId = leadingBidderId;
        this.message = message;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public String getLeadingBidderId() {
        return leadingBidderId;
    }

    public String getMessage() {
        return message;
    }
}
