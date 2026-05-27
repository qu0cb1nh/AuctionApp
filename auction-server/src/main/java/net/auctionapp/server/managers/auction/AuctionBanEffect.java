package net.auctionapp.server.managers.auction;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.NotificationManager;
import net.auctionapp.server.managers.WalletManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public final class AuctionBanEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionBanEffect.class);

    private final ConcurrentMap<String, Auction> auctions;
    private final AuctionSafeUpdateExecutor auctionMutations;
    private final AuthManager authManager;
    private final NotificationManager notificationManager;
    private final WalletManager walletManager;
    private final AuctionPersistence auctionPersistence;
    private final AuctionBroadcaster auctionBroadcaster;

    public AuctionBanEffect(
            ConcurrentMap<String, Auction> auctions,
            AuctionSafeUpdateExecutor auctionMutations,
            AuthManager authManager,
            NotificationManager notificationManager,
            WalletManager walletManager,
            AuctionPersistence auctionPersistence,
            AuctionBroadcaster auctionBroadcaster
    ) {
        this.auctions = auctions;
        this.auctionMutations = auctionMutations;
        this.authManager = authManager;
        this.notificationManager = notificationManager;
        this.walletManager = walletManager;
        this.auctionPersistence = auctionPersistence;
        this.auctionBroadcaster = auctionBroadcaster;
    }

    public void applyUserBanEffects(String bannedUserId) {
        String normalizedBannedUserId = StringUtil.normalizeString(bannedUserId);
        if (normalizedBannedUserId.isEmpty()) {
            throw new ValidationException("User not found.");
        }

        List<BanAuctionChange> changes = new ArrayList<>();
        List<BidTransaction> invalidatedBids = new ArrayList<>();
        Map<String, BigDecimal> fundsToRelease = new HashMap<>();
        auctionMutations.executeWithLock(() -> {
            for (Auction auction : auctions.values()) {
                if (auction.getStatus() != AuctionStatus.RUNNING) {
                    continue;
                }
                Auction candidate = auction.snapshotCopy();
                BanAuctionChange sellerChange = buildSellerBanChange(
                        auction,
                        candidate,
                        normalizedBannedUserId,
                        fundsToRelease
                );
                if (sellerChange != null) {
                    changes.add(sellerChange);
                    continue;
                }
                BanAuctionChange bidderChange = buildBidderBanChange(
                        auction,
                        candidate,
                        normalizedBannedUserId,
                        invalidatedBids,
                        fundsToRelease
                );
                if (bidderChange != null) {
                    changes.add(bidderChange);
                }
            }

            List<Auction> changedAuctions = changes.stream()
                    .map(BanAuctionChange::updatedAuction)
                    .toList();
            auctionPersistence.persistUserBanEffects(
                    normalizedBannedUserId,
                    changedAuctions,
                    invalidatedBids,
                    fundsToRelease
            );
            for (BanAuctionChange change : changes) {
                change.originalAuction().applySnapshot(change.updatedAuction());
            }
            walletManager.applyReleasedFunds(fundsToRelease);
            authManager.applyPersistedUserBanStatus(authManager.requireUserById(normalizedBannedUserId), true);
        });

        for (BanAuctionChange change : changes) {
            broadcastBanChange(change);
        }
    }

    private BanAuctionChange buildSellerBanChange(
            Auction originalAuction,
            Auction candidate,
            String bannedUserId,
            Map<String, BigDecimal> fundsToRelease
    ) {
        if (!bannedUserId.equals(StringUtil.normalizeString(candidate.getSellerId()))) {
            return null;
        }
        candidate.setWinnerBidderId(null);
        candidate.setStatus(AuctionStatus.CANCELED);
        mergeFundsToRelease(fundsToRelease, walletManager.getCommittedAmountsByBidder(candidate));
        return new BanAuctionChange(originalAuction, candidate, true, false);
    }

    private BanAuctionChange buildBidderBanChange(
            Auction originalAuction,
            Auction candidate,
            String bannedUserId,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        String previousLeaderId = StringUtil.normalizeString(candidate.getLeadingBidderId());
        BigDecimal bidderCommitment = walletManager.getBidderCommitment(candidate, bannedUserId);
        List<BidTransaction> newlyInvalidatedBids = candidate.invalidateActiveBidsBy(bannedUserId);
        if (newlyInvalidatedBids.isEmpty()) {
            return null;
        }
        for (BidTransaction invalidatedBid : newlyInvalidatedBids) {
            if (!("restored-" + invalidatedBid.getAuctionId()).equals(invalidatedBid.getId())) {
                invalidatedBids.add(invalidatedBid);
            }
        }
        addFundsToRelease(fundsToRelease, bannedUserId, bidderCommitment);
        boolean leadingBidderChanged = !previousLeaderId.equals(
                StringUtil.normalizeString(candidate.getLeadingBidderId()));
        return new BanAuctionChange(originalAuction, candidate, false, leadingBidderChanged);
    }

    private void broadcastBanChange(BanAuctionChange change) {
        if (change.sellerAuctionCanceled()) {
            auctionBroadcaster.broadcastAuctionStatusChanged(change.originalAuction());
            return;
        }
        auctionBroadcaster.broadcastPriceUpdate(change.originalAuction());
        if (change.leadingBidderChanged()) {
            sendBidRemovalNotifications(change.originalAuction());
        }
    }

    private void mergeFundsToRelease(
            Map<String, BigDecimal> fundsToRelease,
            Map<String, BigDecimal> auctionCommitments
    ) {
        for (Map.Entry<String, BigDecimal> entry : auctionCommitments.entrySet()) {
            addFundsToRelease(fundsToRelease, entry.getKey(), entry.getValue());
        }
    }

    private void addFundsToRelease(Map<String, BigDecimal> fundsToRelease, String bidderId, BigDecimal amount) {
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        if (normalizedBidderId.isEmpty() || amount == null || amount.signum() <= 0) {
            return;
        }
        fundsToRelease.merge(normalizedBidderId, amount, BigDecimal::add);
    }

    private void sendBidRemovalNotifications(Auction auction) {
        try {
            notificationManager.sendBidRemovalNotifications(
                    auction.getId(),
                    auction.getItem().getTitle(),
                    auction.getSellerId(),
                    auction.getLeadingBidderId()
            );
        } catch (DatabaseException e) {
            LOGGER.warn("Failed to send bid-removal notifications for {}: {}", auction.getId(), e.getMessage());
        }
    }

    private record BanAuctionChange(
            Auction originalAuction,
            Auction updatedAuction,
            boolean sellerAuctionCanceled,
            boolean leadingBidderChanged
    ) {
    }
}
