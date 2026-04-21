package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.models.auction.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class AuctionDetailsResponseMessage extends Message {
    private String auctionId;
    private String sellerId;
    private String title;
    private String description;
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private BigDecimal minimumNextBid;
    private AuctionStatus status;
    private String leadingBidderId;
    private String winnerBidderId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
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
        super(MessageType.AUCTION_DETAILS_RESPONSE);
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.minimumNextBid = minimumNextBid;
        this.status = status;
        this.leadingBidderId = leadingBidderId;
        this.winnerBidderId = winnerBidderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bidHistory = bidHistory;
    }

    public String getAuctionId() { return auctionId; }
    public String getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getMinimumNextBid() { return minimumNextBid; }
    public AuctionStatus getStatus() { return status; }
    public String getLeadingBidderId() { return leadingBidderId; }
    public String getWinnerBidderId() { return winnerBidderId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public List<BidView> getBidHistory() { return bidHistory; }
}
