package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidResultMessage extends Message {
    private String auctionId;
    private BigDecimal currentPrice;
    private String leadingBidderId;
    private LocalDateTime endTime;
    private String message;

    public BidResultMessage() {
        super();
    }

    public BidResultMessage(MessageType type, String auctionId, BigDecimal currentPrice, String leadingBidderId, String message) {
        this(type, auctionId, currentPrice, leadingBidderId, null, message);
    }

    public BidResultMessage(
            MessageType type,
            String auctionId,
            BigDecimal currentPrice,
            String leadingBidderId,
            LocalDateTime endTime,
            String message
    ) {
        super(type);
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.leadingBidderId = leadingBidderId;
        this.endTime = endTime;
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

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getMessage() {
        return message;
    }
}
