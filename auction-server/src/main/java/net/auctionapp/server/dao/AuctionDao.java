package net.auctionapp.server.dao;

import net.auctionapp.common.models.auction.Auction;

import java.util.List;

public interface AuctionDao {
    List<Auction> findAllAuctions();

    boolean createAuction(Auction auction);

    boolean updateAuctionState(Auction auction);
}
