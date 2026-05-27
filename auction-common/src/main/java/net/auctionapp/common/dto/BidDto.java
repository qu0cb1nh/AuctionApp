package net.auctionapp.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidDto {
    private String bidId;
    private String bidderId;
    private BigDecimal amount;
    private LocalDateTime timestamp;

    public BidDto() {
    }

    public BidDto(String bidId, String bidderId, BigDecimal amount, LocalDateTime timestamp) {
        this.bidId = bidId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getBidId() {
        return bidId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
