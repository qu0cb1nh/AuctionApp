package net.auctionapp.common.messages.types;

import net.auctionapp.common.models.auction.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionSummary {
    private final String auctionId;
    private final String title;
    private final BigDecimal currentPrice;
    private final BigDecimal minimumNextBid;
    private final AuctionStatus status;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public AuctionSummary(
            String auctionId,
            String title,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        this.auctionId = auctionId;
        this.title = title;
        this.currentPrice = currentPrice;
        this.minimumNextBid = minimumNextBid;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getMinimumNextBid() {
        return minimumNextBid;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
}
