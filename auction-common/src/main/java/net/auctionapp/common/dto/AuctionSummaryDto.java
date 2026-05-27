package net.auctionapp.common.dto;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionSummaryDto {
    private String auctionId;
    private String title;
    private BigDecimal currentPrice;
    private BigDecimal minimumNextBid;
    private AuctionStatus status;
    private String leadingBidderId;
    private String leadingBidderUsername;
    private String sellerId;
    private String sellerUsername;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String imageUrl;
    private ItemType itemType;

    public AuctionSummaryDto() {
    }

    public AuctionSummaryDto(
            String auctionId,
            String title,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        this(
                auctionId,
                title,
                currentPrice,
                minimumNextBid,
                status,
                leadingBidderId,
                startTime,
                endTime,
                null,
                null,
                leadingBidderId,
                null,
                null
        );
    }

    public AuctionSummaryDto(
            String auctionId,
            String title,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String imageUrl,
            ItemType itemType,
            String leadingBidderUsername,
            String sellerId,
            String sellerUsername
    ) {
        this.auctionId = auctionId;
        this.title = title;
        this.currentPrice = currentPrice;
        this.minimumNextBid = minimumNextBid;
        this.status = status;
        this.leadingBidderId = leadingBidderId;
        this.leadingBidderUsername = leadingBidderUsername;
        this.sellerId = sellerId;
        this.sellerUsername = sellerUsername;
        this.startTime = startTime;
        this.endTime = endTime;
        this.imageUrl = imageUrl;
        this.itemType = itemType;
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

    public String getLeadingBidderUsername() {
        return leadingBidderUsername == null || leadingBidderUsername.isBlank()
                ? leadingBidderId
                : leadingBidderUsername;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getSellerUsername() {
        return sellerUsername == null || sellerUsername.isBlank()
                ? sellerId
                : sellerUsername;
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

    public ItemType getItemType() {
        return itemType;
    }
}
