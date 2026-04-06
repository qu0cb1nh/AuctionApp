package net.auctionapp.common.models.users;

import net.auctionapp.common.models.auction.AutoBidConfig;
import net.auctionapp.common.models.auction.BidTransaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user who can participate in auctions by placing bids.
 */
public class Bidder extends User {

    private final List<BidTransaction> bidHistory;
    private AutoBidConfig autoBidConfig;

    public Bidder(String id, String username, String passwordHash) {
        super(id, username, passwordHash, UserRole.BIDDER);
        this.bidHistory = new ArrayList<>();
    }

    /**
     * Gets the bidding history of this bidder.
     *
     * @return A list of bid transactions.
     */
    public List<BidTransaction> getBidHistory() {
        return bidHistory;
    }

    /**
     * Adds a bid to the bidder's history.
     *
     * @param bid The bid transaction to add.
     */
    public void addBidToHistory(BidTransaction bid) {
        this.bidHistory.add(bid);
    }

    /**
     * Gets the auto-bidding configuration for this bidder.
     *
     * @return The auto-bid configuration, or null if not set.
     */
    public AutoBidConfig getAutoBidConfig() {
        return autoBidConfig;
    }

    /**
     * Sets or updates the auto-bidding configuration.
     *
     * @param autoBidConfig The new auto-bid configuration.
     */
    public void setAutoBidConfig(AutoBidConfig autoBidConfig) {
        this.autoBidConfig = autoBidConfig;
    }
}
