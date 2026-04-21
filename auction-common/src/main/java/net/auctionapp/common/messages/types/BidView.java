package net.auctionapp.common.messages.types;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidView {
    private final String bidId;
    private final String bidderId;
    private final BigDecimal amount;
    private final LocalDateTime timestamp;

    public BidView(String bidId, String bidderId, BigDecimal amount, LocalDateTime timestamp) {
        this.bidId = bidId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getBidId() {
        return bidId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
