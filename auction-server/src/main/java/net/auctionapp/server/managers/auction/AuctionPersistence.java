package net.auctionapp.server.managers.auction;

import net.auctionapp.server.dao.AuctionDao;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.InsufficientFundsException;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.managers.WalletManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class AuctionPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionPersistence.class);

    private final ConcurrentMap<String, Auction> auctions;
    private final WalletManager walletManager;
    private volatile AuctionDao auctionDao;

    public AuctionPersistence(ConcurrentMap<String, Auction> auctions, WalletManager walletManager) {
        this.auctions = auctions;
        this.walletManager = walletManager;
    }

    public synchronized void setAuctionDao(AuctionDao auctionDao) {
        this.auctionDao = Objects.requireNonNull(auctionDao, "Auction DAO is required.");
        List<Auction> persistedAuctions = auctionDao.findAllAuctions();
        auctions.clear();
        for (Auction auction : persistedAuctions) {
            auctions.put(auction.getId(), auction);
        }
        LOGGER.info("Loaded {} auction(s) from persistence.", persistedAuctions.size());
    }

    public void persistAuction(Auction auction) {
        executeDaoAction(dao -> requireSuccess(dao.createAuction(auction), "Auction could not be persisted."));
        LOGGER.debug("Persisted new auction {}.", auction.getId());
    }

    public void persistAuctionState(Auction auction) {
        walletManager.closeAuctionWallets(auction);
        LOGGER.info("Persisted auction state for auction {} with status {}.", auction.getId(), auction.getStatus());
    }

    public void persistAcceptedBid(Auction auction, BidTransaction bid, BigDecimal amountToLock) {
        executeDaoAction(dao -> {
            if (!dao.recordBid(auction, bid, amountToLock)) {
                throw new InsufficientFundsException("Insufficient balance. Please check your wallet.");
            }
        });
        LOGGER.debug("Persisted bid {} for auction {} and locked amount {}.", bid.getId(), auction.getId(), amountToLock);
    }

    public void persistCanceledBids(
            Auction auction,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        executeDaoAction(dao -> requireSuccess(
                dao.cancelBids(auction, invalidatedBids, fundsToRelease),
                "Canceled bids could not be persisted."
        ));
        LOGGER.info(
                "Persisted {} canceled bid(s) for auction {}.",
                invalidatedBids == null ? 0 : invalidatedBids.size(),
                auction.getId()
        );
    }

    public void persistAuctionDetails(Auction auction) {
        executeDaoAction(dao -> requireSuccess(dao.updateAuction(auction), "Auction details could not be persisted."));
        LOGGER.info("Persisted auction detail update for auction {}.", auction.getId());
    }

    public void persistUserBanEffects(
            String bannedUserId,
            List<Auction> changedAuctions,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        executeDaoAction(dao -> requireSuccess(
                dao.applyUserBanEffects(bannedUserId, changedAuctions, invalidatedBids, fundsToRelease),
                "User ban effects could not be persisted."
        ));
        LOGGER.info(
                "Persisted user ban effects for user {}. Changed auctions: {}, invalidated bids: {}, released fund entries: {}.",
                bannedUserId,
                changedAuctions == null ? 0 : changedAuctions.size(),
                invalidatedBids == null ? 0 : invalidatedBids.size(),
                fundsToRelease == null ? 0 : fundsToRelease.size()
        );
    }

    private void executeDaoAction(Consumer<AuctionDao> action) {
        AuctionDao dao = auctionDao;
        if (dao == null) {
            throw new DatabaseException("Auction persistence is not configured.");
        }
        try {
            action.accept(dao);
        } catch (DatabaseException e) {
            throw new DatabaseException("Auction persistence operation failed.", e);
        }
    }

    private void requireSuccess(boolean successful, String errorMessage) {
        if (!successful) {
            throw new DatabaseException(errorMessage);
        }
    }
}
