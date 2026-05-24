package net.auctionapp.common.messages.types;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionSummary {
    private final String auctionId;
    private final String title;
    private final BigDecimal currentPrice;
    private final BigDecimal minimumNextBid;
    private final AuctionStatus status;
    private final String leadingBidderId;
    private final String leadingBidderUsername;
    private final String sellerId;
    private final String sellerUsername;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String imageUrl;
    private final ItemType itemType;

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
        this(auctionId, title, currentPrice, minimumNextBid, status, leadingBidderId, startTime, endTime, null, null);
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
        this(auctionId, title, currentPrice, minimumNextBid, status, leadingBidderId, startTime, endTime, imageUrl, null);
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
            String imageUrl,
            ItemType itemType
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
                imageUrl,
                itemType,
                leadingBidderId
        );
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
            String imageUrl,
            ItemType itemType,
            String leadingBidderUsername
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
                imageUrl,
                itemType,
                leadingBidderUsername,
                null,
                null
        );
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
