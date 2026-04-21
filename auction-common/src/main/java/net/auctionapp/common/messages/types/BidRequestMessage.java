package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

/**
 * A message from the Client to the Server to place a bid.
 * Inherits from Message and has the type BID_REQUEST.
 */
public class BidRequestMessage extends Message {
    private String itemId;
    private double price;
    private String userName; // Temporarily using userName, could be userId or a token later

    // Default constructor is needed for Gson
    public BidRequestMessage() {
        super(MessageType.BID_REQUEST);
    }

    public BidRequestMessage(String itemId, double price, String userName) {
        super(MessageType.BID_REQUEST);
        this.itemId = itemId;
        this.price = price;
        this.userName = userName;
    }

    // Getters and Setters
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
