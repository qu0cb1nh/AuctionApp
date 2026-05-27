package net.auctionapp.common.dto;

import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ListingSummaryDto {
    private String auctionId;
    private String title;
    private String status;
    private String sellerUsername;
    private BigDecimal currentPrice;
    private LocalDateTime endTime;
    private String bidderCaption;
    private String bidderValue;
    private String imageUrl;
    private ItemType itemType;

    public ListingSummaryDto() {
    }

    public ListingSummaryDto(
            String auctionId,
            String title,
            String status,
            String sellerUsername,
            BigDecimal currentPrice,
            LocalDateTime endTime,
            String bidderCaption,
            String bidderValue,
            String imageUrl,
            ItemType itemType
    ) {
        this.auctionId = auctionId;
        this.title = title;
        this.status = status;
        this.sellerUsername = sellerUsername;
        this.currentPrice = currentPrice;
        this.endTime = endTime;
        this.bidderCaption = bidderCaption;
        this.bidderValue = bidderValue;
        this.imageUrl = imageUrl;
        this.itemType = itemType;
    }

    public String getAuctionId() { return auctionId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getSellerUsername() { return sellerUsername; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getBidderCaption() { return bidderCaption; }
    public String getBidderValue() { return bidderValue; }
    public String getImageUrl() { return imageUrl; }
    public ItemType getItemType() { return itemType; }
}
