package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class BidRequestMessage extends Message {
    private String itemId;
    private BigDecimal price;

    public BidRequestMessage() {
        super(MessageType.BID_REQUEST);
    }

    public BidRequestMessage(String auctionId, BigDecimal price) {
        super(MessageType.BID_REQUEST);
        this.itemId = auctionId;
        this.price = price;
    }

    public String getAuctionId() {
        return itemId;
    }

    public void setAuctionId(String auctionId) {
        this.itemId = auctionId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

}
