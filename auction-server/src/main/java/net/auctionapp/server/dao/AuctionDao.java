package net.auctionapp.server.dao;

import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AuctionDao {
    List<Auction> findAllAuctions();

    boolean createAuction(Auction auction);

    boolean recordBid(Auction auction, BidTransaction bid, BigDecimal amountToLock);

    boolean updateAuction(Auction auction);

    boolean settleAuction(Auction auction, Map<String, BigDecimal> committedAmountsByBidder);

    boolean applyUserBanEffects(
            String bannedUserId,
            List<Auction> updatedAuctions,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    );
}
