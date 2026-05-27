package net.auctionapp.server.managers.auction;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.WalletManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AuctionBid {
    private final AuctionSafeUpdateExecutor auctionMutations;
    private final AuthManager authManager;
    private final WalletManager walletManager;
    private final AuctionQuery auctionQuery;
    private final AuctionPersistence auctionPersistence;
    private final Clock clock;

    public AuctionBid(
            AuctionSafeUpdateExecutor auctionMutations,
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

    public BidResult submitBid(String auctionId, String bidderId, BigDecimal amount) {
        Auction auction = auctionQuery.requireAuction(auctionId);
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        BidMutation mutation = auctionMutations.executeWithLock(
                auction,
                candidate -> {
                    authManager.requireActiveUserById(normalizedBidderId);
                    BidTransaction bid = new BidTransaction(
                            UUID.randomUUID().toString(),
                            amount,
                            LocalDateTime.now(clock),
                            normalizedBidderId,
                            auctionId
                    );

                    String previousLeadingBidderId = auction.getLeadingBidderId();
                    candidate.placeBid(bid);

                    BigDecimal existingCommitment = walletManager.getBidderCommitment(auction, normalizedBidderId);
                    BigDecimal incrementalAmount = amount.subtract(existingCommitment);
                    if (incrementalAmount.signum() <= 0) {
                        throw new ValidationException("Your new bid must be higher than your existing commitment.");
                    }
                    return new BidMutation(bid, incrementalAmount, previousLeadingBidderId);
                },
                (candidate, result) -> auctionPersistence.persistAcceptedBid(
                        candidate,
                        result.bid(),
                        result.incrementalAmount()
                ),
                (candidate, result) -> walletManager.applyLockedBidFunds(
                        normalizedBidderId,
                        result.incrementalAmount()
                )
        );
        return new BidResult(auction, mutation.previousLeadingBidderId());
    }

    public record BidResult(Auction auction, String previousLeadingBidderId) {
    }

    private record BidMutation(BidTransaction bid, BigDecimal incrementalAmount, String previousLeadingBidderId) {
    }
}
