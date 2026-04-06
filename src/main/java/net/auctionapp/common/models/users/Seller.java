package net.auctionapp.common.models.users;

import net.auctionapp.common.models.auction.Auction;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user who can sell items by creating auctions.
 */
public class Seller extends User {

    private final List<Auction> listedAuctions;

    public Seller(String id, String username, String passwordHash) {
        super(id, username, passwordHash, UserRole.SELLER);
        this.listedAuctions = new ArrayList<>();
    }

    /**
     * Gets the list of auctions created by this seller.
     *
     * @return A list of auctions.
     */
    public List<Auction> getListedAuctions() {
        return listedAuctions;
    }

    /**
     * Adds an auction to the seller's list of created auctions.
     *
     * @param auction The auction to add.
     */
    public void addAuction(Auction auction) {
        this.listedAuctions.add(auction);
    }
}
