package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

/**
 * A message from the Server to all Clients to announce a new price.
 * Inherits from Message and has the type PRICE_UPDATE.
 */
public class PriceUpdateMessage extends Message {
    private String itemId;
    private double newPrice;
    private String leadingUserName;

    // Default constructor is needed for Gson
    public PriceUpdateMessage() {
        super(MessageType.PRICE_UPDATE);
    }

    public PriceUpdateMessage(String itemId, double newPrice, String leadingUserName) {
        super(MessageType.PRICE_UPDATE);
        this.itemId = itemId;
        this.newPrice = newPrice;
        this.leadingUserName = leadingUserName;
    }

    // Getters and Setters
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
