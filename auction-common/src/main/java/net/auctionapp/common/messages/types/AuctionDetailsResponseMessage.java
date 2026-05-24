package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class AuctionDetailsResponseMessage extends Message {
    private String auctionId;
    private String sellerId;
    private String sellerUsername;
    private String title;
    private String description;
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private BigDecimal minimumNextBid;
    private AuctionStatus status;
    private String leadingBidderId;
    private String winnerBidderId;
    private String leadingBidderUsername;
    private String winnerBidderUsername;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String imageUrl;
    private ItemType itemType;
    private List<BidView> bidHistory;

    public AuctionDetailsResponseMessage() {
        super(MessageType.AUCTION_DETAILS_RESPONSE);
    }

    public AuctionDetailsResponseMessage(
            String auctionId,
            String sellerId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            String winnerBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<BidView> bidHistory
    ) {
        this(
                auctionId,
                sellerId,
                title,
                description,
                startingPrice,
                currentPrice,
                minimumNextBid,
                status,
                leadingBidderId,
                winnerBidderId,
                startTime,
                endTime,
                null,
                null,
                bidHistory
        );
    }

    public AuctionDetailsResponseMessage(
            String auctionId,
            String sellerId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            String winnerBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String imageUrl,
            List<BidView> bidHistory
    ) {
        this(
                auctionId,
                sellerId,
                title,
                description,
                startingPrice,
                currentPrice,
                minimumNextBid,
                status,
                leadingBidderId,
                winnerBidderId,
                startTime,
                endTime,
                imageUrl,
                null,
                bidHistory
        );
    }

    public AuctionDetailsResponseMessage(
            String auctionId,
            String sellerId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            String winnerBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String imageUrl,
            ItemType itemType,
            List<BidView> bidHistory
    ) {
        this(
                auctionId,
                sellerId,
                title,
                description,
                startingPrice,
                currentPrice,
                minimumNextBid,
                status,
                leadingBidderId,
                winnerBidderId,
                startTime,
                endTime,
                imageUrl,
                itemType,
                bidHistory,
                leadingBidderId,
                winnerBidderId
        );
    }

    public AuctionDetailsResponseMessage(
            String auctionId,
            String sellerId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            String winnerBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String imageUrl,
            ItemType itemType,
            List<BidView> bidHistory,
            String leadingBidderUsername,
            String winnerBidderUsername
    ) {
        this(
                auctionId,
                sellerId,
                title,
                description,
                startingPrice,
                currentPrice,
                minimumNextBid,
                status,
                leadingBidderId,
                winnerBidderId,
                startTime,
                endTime,
                imageUrl,
                itemType,
                bidHistory,
                leadingBidderUsername,
                winnerBidderUsername,
                sellerId
        );
    }

    public AuctionDetailsResponseMessage(
            String auctionId,
            String sellerId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minimumNextBid,
            AuctionStatus status,
            String leadingBidderId,
            String winnerBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String imageUrl,
            ItemType itemType,
            List<BidView> bidHistory,
            String leadingBidderUsername,
            String winnerBidderUsername,
            String sellerUsername
    ) {
        super(MessageType.AUCTION_DETAILS_RESPONSE);
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.sellerUsername = sellerUsername;
        this.title = title;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.minimumNextBid = minimumNextBid;
        this.status = status;
        this.leadingBidderId = leadingBidderId;
        this.winnerBidderId = winnerBidderId;
        this.leadingBidderUsername = leadingBidderUsername;
        this.winnerBidderUsername = winnerBidderUsername;
        this.startTime = startTime;
        this.endTime = endTime;
        this.imageUrl = imageUrl;
        this.itemType = itemType;
        this.bidHistory = bidHistory == null ? List.of() : List.copyOf(bidHistory);
    }

    public String getAuctionId() { return auctionId; }
    public String getSellerId() { return sellerId; }
    public String getSellerUsername() {
        return sellerUsername == null || sellerUsername.isBlank() ? sellerId : sellerUsername;
    }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getMinimumNextBid() { return minimumNextBid; }
    public AuctionStatus getStatus() { return status; }
    public String getLeadingBidderId() { return leadingBidderId; }
    public String getWinnerBidderId() { return winnerBidderId; }
    public String getLeadingBidderUsername() {
        return leadingBidderUsername == null || leadingBidderUsername.isBlank()
                ? leadingBidderId
                : leadingBidderUsername;
    }
    public String getWinnerBidderUsername() {
        return winnerBidderUsername == null || winnerBidderUsername.isBlank()
                ? winnerBidderId
                : winnerBidderUsername;
    }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getImageUrl() { return imageUrl; }
    public ItemType getItemType() { return itemType; }
    public List<BidView> getBidHistory() { return bidHistory == null ? List.of() : List.copyOf(bidHistory); }
}
