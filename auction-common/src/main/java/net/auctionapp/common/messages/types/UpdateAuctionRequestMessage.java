package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UpdateAuctionRequestMessage extends Message {
    private String auctionId;
    private String title;
    private String description;
    private BigDecimal startingPrice;
    private BigDecimal minimumBidIncrement;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public UpdateAuctionRequestMessage() {
        super(MessageType.UPDATE_AUCTION_REQUEST);
    }

    public UpdateAuctionRequestMessage(
            String auctionId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        super(MessageType.UPDATE_AUCTION_REQUEST);
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
