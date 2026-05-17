package net.auctionapp.common.messages.types;

import net.auctionapp.common.auction.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionSummary {
    private final String auctionId;
    private final String title;
    private final BigDecimal currentPrice;
    private final BigDecimal minimumNextBid;
    private final AuctionStatus status;
    private final String leadingBidderId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String imageUrl;

    public AuctionSummary(
            String auctionId,
            String title,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        this(auctionId, title, currentPrice, minimumNextBid, status, leadingBidderId, startTime, endTime, null);
    }

    public AuctionSummary(
            String auctionId,
            String title,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String imageUrl
    ) {
        this.auctionId = auctionId;
        this.title = title;
        this.currentPrice = currentPrice;
        this.minimumNextBid = minimumNextBid;
        this.status = status;
        this.leadingBidderId = leadingBidderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.imageUrl = imageUrl;
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

    public String getLeadingBidderId() {
        return leadingBidderId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
