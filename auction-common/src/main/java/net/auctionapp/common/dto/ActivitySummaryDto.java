package net.auctionapp.common.dto;

import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ActivitySummaryDto {
    private String auctionId;
    private String title;
    private String status;
    private String bidPosition;
    private String sellerId;
    private String sellerUsername;
    private BigDecimal yourMaxBid;
    private BigDecimal currentPrice;
    private int bidCount;
    private String winnerBidderName;
    private LocalDateTime endTime;
    private String imageUrl;
    private ItemType itemType;

    public ActivitySummaryDto() {
    }

    public ActivitySummaryDto(
            String auctionId,
            String title,
            String status,
            String bidPosition,
            String sellerId,
            String sellerUsername,
            BigDecimal yourMaxBid,
            BigDecimal currentPrice,
            int bidCount,
            String winnerBidderName,
            LocalDateTime endTime,
            String imageUrl,
            ItemType itemType
    ) {
        this.auctionId = auctionId;
        this.title = title;
        this.status = status;
        this.bidPosition = bidPosition;
        this.sellerId = sellerId;
        this.sellerUsername = sellerUsername;
        this.yourMaxBid = yourMaxBid;
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
        this.winnerBidderName = winnerBidderName;
        this.endTime = endTime;
        this.imageUrl = imageUrl;
        this.itemType = itemType;
    }

    public String getAuctionId() { return auctionId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getBidPosition() { return bidPosition; }
    public String getSellerId() { return sellerId; }
    public String getSellerUsername() { return sellerUsername; }
    public BigDecimal getYourMaxBid() { return yourMaxBid; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public int getBidCount() { return bidCount; }
    public String getWinnerBidderName() { return winnerBidderName; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getImageUrl() { return imageUrl; }
    public ItemType getItemType() { return itemType; }
}
