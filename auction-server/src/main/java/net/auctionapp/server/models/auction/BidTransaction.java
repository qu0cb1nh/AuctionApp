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
    private final BidStatus status;

    public BidTransaction(String id, BigDecimal amount, LocalDateTime timestamp, String bidderId, String auctionId) {
        this(id, amount, timestamp, bidderId, auctionId, BidStatus.ACTIVE);
    }

    public BidTransaction(
            String id,
            BigDecimal amount,
            LocalDateTime timestamp,
            String bidderId,
            String auctionId,
            BidStatus status
    ) {
        super(id);
        this.amount = amount;
        this.timestamp = timestamp;
        this.bidderId = bidderId;
        this.auctionId = auctionId;
        this.status = status == null ? BidStatus.ACTIVE : status;
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

    public BidStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == BidStatus.ACTIVE;
    }

    public BidTransaction invalidate() {
        return new BidTransaction(getId(), amount, timestamp, bidderId, auctionId, BidStatus.INVALIDATED);
    }
}
