package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AdminUpdateAuctionRequestMessage extends Message {
    private String auctionId;
    private String title;
    private String description;
    private BigDecimal startingPrice;
    private BigDecimal minimumBidIncrement;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public AdminUpdateAuctionRequestMessage() {
        super(MessageType.ADMIN_UPDATE_AUCTION_REQUEST);
    }

    public AdminUpdateAuctionRequestMessage(
            String auctionId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        super(MessageType.ADMIN_UPDATE_AUCTION_REQUEST);
        this.auctionId = auctionId;
        this.title = title;
        this.description = description;
        this.startingPrice = startingPrice;
        this.minimumBidIncrement = minimumBidIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public BigDecimal getMinimumBidIncrement() {
        return minimumBidIncrement;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
}
