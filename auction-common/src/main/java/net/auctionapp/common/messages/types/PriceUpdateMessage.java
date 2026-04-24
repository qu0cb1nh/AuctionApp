package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class PriceUpdateMessage extends Message {
    private String itemId;
    private double newPrice;
    private String leadingUserName;

    public PriceUpdateMessage() {
        super(MessageType.PRICE_UPDATE);
    }

    public PriceUpdateMessage(String auctionId, double newPrice, String leadingUserName) {
        super(MessageType.PRICE_UPDATE);
        this.itemId = auctionId;
        this.newPrice = newPrice;
        this.leadingUserName = leadingUserName;
    }

    public String getAuctionId() {
        return itemId;
    }

    public void setAuctionId(String auctionId) {
        this.itemId = auctionId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public double getNewPrice() {
        return newPrice;
    }

    public void setNewPrice(double newPrice) {
        this.newPrice = newPrice;
    }

    public String getLeadingUserName() {
        return leadingUserName;
    }

    public void setLeadingUserName(String leadingUserName) {
        this.leadingUserName = leadingUserName;
    }
}
