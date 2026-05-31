package net.auctionapp.server.managers.auction;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.WalletManager;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AuctionBidCancellation {
    private final AuctionMutationExecutor auctionMutations;
    private final AuthManager authManager;
    private final WalletManager walletManager;
    private final AuctionQuery auctionQuery;
    private final AuctionPersistence auctionPersistence;
    private final Clock clock;

    public AuctionBidCancellation(
            AuctionMutationExecutor auctionMutations,
            AuthManager authManager,
            WalletManager walletManager,
            AuctionQuery auctionQuery,
            AuctionPersistence auctionPersistence,
            Clock clock
    ) {
        this.auctionMutations = auctionMutations;
        this.authManager = authManager;
        this.walletManager = walletManager;
        this.auctionQuery = auctionQuery;
        this.auctionPersistence = auctionPersistence;
        this.clock = clock;
    }

    public CancelBidsResult cancelBids(String auctionId, String bidderId) {
        Auction auction = auctionQuery.requireAuction(auctionId);
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        return auctionMutations.executeWithLock(
                auction,
                managedAuction -> {
                    authManager.requireActiveUserById(normalizedBidderId);
                    requireOpenForBidCancellation(managedAuction);
                    if (normalizedBidderId.equals(StringUtil.normalizeString(managedAuction.getLeadingBidderId()))) {
                        throw new ValidationException("You cannot cancel your leading bid.");
                    }

                    BigDecimal amountToRelease = walletManager.getBidderCommitment(managedAuction, normalizedBidderId);
                    if (amountToRelease.signum() <= 0) {
                        throw new ValidationException("No active bids to cancel.");
                    }

                    List<BidTransaction> invalidatedBids = managedAuction.invalidateActiveBidsBy(normalizedBidderId);
                    if (invalidatedBids.isEmpty()) {
                        throw new ValidationException("No active bids to cancel.");
                    }

                    Map<String, BigDecimal> fundsToRelease = Map.of(normalizedBidderId, amountToRelease);
                    return new CancelBidsMutation(invalidatedBids, fundsToRelease);
                },
                (managedAuction, mutation) -> auctionPersistence.persistCanceledBids(
                        managedAuction,
                        mutation.invalidatedBids(),
                        mutation.fundsToRelease()
                ),
                (managedAuction, mutation) -> walletManager.applyReleasedFunds(mutation.fundsToRelease())
        ).toResult(auction);
    }

    private void requireOpenForBidCancellation(Auction auction) {
        if (auction.getStatus() != AuctionStatus.RUNNING
                || !LocalDateTime.now(clock).isBefore(auction.getEndTime())) {
            throw new InvalidAuctionStateException("Auction is not open for bid cancellation.");
        }
    }

    public record CancelBidsResult(Auction auction, int canceledBidCount) {
    }

    private record CancelBidsMutation(
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        private CancelBidsResult toResult(Auction auction) {
            return new CancelBidsResult(auction, invalidatedBids.size());
        }
    }
}
