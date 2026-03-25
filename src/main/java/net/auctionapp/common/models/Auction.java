package net.auctionapp.common.models;

/**
 * Represents an auction session.
 */
public class Auction {
    private String itemId;
    private String itemName;
    private double currentPrice;
    private User leadingBidder;
    // You can add other states like start/end time, etc.

    public Auction(String itemId, String itemName, double startPrice) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.currentPrice = startPrice;
        this.leadingBidder = null; // Initially, there is no leading bidder
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public User getLeadingBidder() {
        return leadingBidder;
    }

    public void setLeadingBidder(User leadingBidder) {
        this.leadingBidder = leadingBidder;
    }
}
