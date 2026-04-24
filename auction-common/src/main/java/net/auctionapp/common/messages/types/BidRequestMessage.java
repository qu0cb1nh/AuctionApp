package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class BidRequestMessage extends Message {
    private String itemId;
    private double price;
    private String userName;

    public BidRequestMessage() {
        super(MessageType.BID_REQUEST);
    }

    public BidRequestMessage(String auctionId, double price) {
        this(auctionId, price, null);
    }

    public BidRequestMessage(String auctionId, double price, String userName) {
        super(MessageType.BID_REQUEST);
        this.itemId = auctionId;
        this.price = price;
        this.userName = userName;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Deprecated(forRemoval = false)
    public String getUserName() {
        return userName;
    }

    @Deprecated(forRemoval = false)
    public void setUserName(String userName) {
        this.userName = userName;
    }
}
