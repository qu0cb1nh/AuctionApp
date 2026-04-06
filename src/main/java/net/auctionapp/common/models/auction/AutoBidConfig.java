package net.auctionapp.common.models.auction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Server-side configuration representing an "auto-bid" registration for a bidder.
 */
public class AutoBidConfig {
    private final String bidderId;
    private final BigDecimal maxBid;
    private final BigDecimal increment;
    private final LocalDateTime registeredAt;

    public AutoBidConfig(String bidderId, BigDecimal maxBid, BigDecimal increment, LocalDateTime registeredAt) {
        this.bidderId = bidderId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registeredAt = registeredAt;
    }

    public String getBidderId() {
        return bidderId;
    }

    public BigDecimal getMaxBid() {
        return maxBid;
    }

    public BigDecimal getIncrement() {
        return increment;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}

