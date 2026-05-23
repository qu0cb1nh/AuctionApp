package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceUpdateMessage extends Message {
    private String itemId;
    private BigDecimal newPrice;
    private String leadingUserName;
    private LocalDateTime endTime;

    public PriceUpdateMessage() {
        super(MessageType.PRICE_UPDATE);
    }

    public PriceUpdateMessage(String auctionId, BigDecimal newPrice, String leadingUserName, LocalDateTime endTime) {
        super(MessageType.PRICE_UPDATE);
        this.itemId = auctionId;
        this.newPrice = newPrice;
        this.leadingUserName = leadingUserName;
        this.endTime = endTime;
    }

    public String getAuctionId() {
        return itemId;
    }

    public void setAuctionId(String auctionId) {
        this.itemId = auctionId;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public void setNewPrice(BigDecimal newPrice) {
        this.newPrice = newPrice;
    }

    public String getLeadingUserName() {
        return leadingUserName;
    }

    public void setLeadingUserName(String leadingUserName) {
        this.leadingUserName = leadingUserName;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
