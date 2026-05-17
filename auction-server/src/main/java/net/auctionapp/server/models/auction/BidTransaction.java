package net.auctionapp.server.models.auction;

import net.auctionapp.server.models.Entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single bid placed by a user on an auction.
 */
public class BidTransaction extends Entity {
    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final String bidderId;
    private final String auctionId;

    public BidTransaction(String id, BigDecimal amount, LocalDateTime timestamp, String bidderId, String auctionId) {
        super(id);
        this.amount = amount;
        this.timestamp = timestamp;
        this.bidderId = bidderId;
        this.auctionId = auctionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getBidderId() {
        return bidderId;
    }

    public String getAuctionId() {
        return auctionId;
    }
}
