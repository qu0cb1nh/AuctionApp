package net.auctionapp.server.managers.auction;

import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.AuthorizationException;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.users.User;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.WatchListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class AuctionLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionLifecycle.class);

    private final ConcurrentMap<String, Auction> auctions;
    private final AuctionSafeUpdateExecutor auctionMutations;
    private final AuthManager authManager;
    private final AuctionQuery auctionQuery;
    private final AuctionPersistence auctionPersistence;
    private final AuctionBroadcaster auctionBroadcaster;
    private final WatchListManager watchListManager;
    private final Clock clock;
    private ScheduledExecutorService statusScheduler;

    public AuctionLifecycle(
            ConcurrentMap<String, Auction> auctions,
            AuctionSafeUpdateExecutor auctionMutations,
            AuthManager authManager,
            AuctionQuery auctionQuery,
            AuctionPersistence auctionPersistence,
            AuctionBroadcaster auctionBroadcaster,
            WatchListManager watchListManager,
            Clock clock
    ) {
        this.auctions = auctions;
        this.auctionMutations = auctionMutations;
        this.authManager = authManager;
        this.auctionQuery = auctionQuery;
        this.auctionPersistence = auctionPersistence;
        this.auctionBroadcaster = auctionBroadcaster;
        this.watchListManager = watchListManager;
        this.clock = clock;
    }

    public Auction updateAuction(
            String actorId,
            String auctionId,
            String title,
            String description,
            LocalDateTime endTime
    ) {
        Auction auction = auctionQuery.requireAuction(auctionId);
        auctionMutations.executeWithLock(
                auction,
                candidate -> {
                    User actor = authManager.requireActiveUserById(StringUtil.normalizeString(actorId));
                    ensureAdminOrOwningSeller(actor, auction);
                    candidate.updateManagedListingDetails(title, description, endTime);
                },
                auctionPersistence::persistAuctionDetails
        );
        LOGGER.info("Auction {} details updated by user {}. New end time: {}.", auction.getId(), actorId, endTime);
        return auction;
    }

    public Auction closeAuction(String actorId, String auctionId) {
        Auction auction = auctionQuery.requireAuction(auctionId);
        User actor = authManager.requireActiveUserById(StringUtil.normalizeString(actorId));
        if (!actor.hasRole(UserRole.ADMIN)) {
            throw new AuthorizationException("Only an admin can close an auction manually.");
        }
        applyStateTransition(auction, Auction::closeManually);
        LOGGER.info("Auction {} manually closed by admin {}.", auction.getId(), actor.getId());
        return auction;
    }

    public Auction cancelAuction(String actorId, String auctionId) {
        Auction auction = auctionQuery.requireAuction(auctionId);
        User actor = authManager.requireActiveUserById(StringUtil.normalizeString(actorId));
        ensureAdminOrOwningSeller(actor, auction);
        boolean actorIsAdmin = actor.hasRole(UserRole.ADMIN);
        applyStateTransition(auction, candidate -> {
            if (!actorIsAdmin && !candidate.getBidHistory().isEmpty()) {
                throw new AuthorizationException("An auction with bids cannot be canceled by its seller.");
            }
            candidate.cancel();
        });
        LOGGER.info("Auction {} canceled by user {}.", auction.getId(), actor.getId());
        return auction;
    }

    public synchronized void start() {
        if (statusScheduler != null && !statusScheduler.isShutdown()) {
            return;
        }
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("auction-status-scheduler");
            thread.setDaemon(true);
            return thread;
        };
        statusScheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        statusScheduler.scheduleAtFixedRate(() -> {
            try {
                refreshStatuses();
            } catch (RuntimeException e) {
                LOGGER.warn("Auction status refresh failed: {}", e.getMessage(), e);
            }
        }, 1, 1, TimeUnit.SECONDS);
        LOGGER.info("Auction status scheduler started.");
    }

    public synchronized void stop() {
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            statusScheduler = null;
            LOGGER.info("Auction status scheduler stopped.");
        }
    }

    public void refreshStatuses() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Auction auction : auctions.values()) {
            try {
                watchListManager.sendEndingSoonReminders(auctionQuery.buildAuctionSummary(auction), now);
                closeAuctionIfEnded(auction);
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to refresh auction {}: {}", auction.getId(), e.getMessage(), e);
            }
        }
    }

    void closeAuctionIfEnded(Auction auction) {
        boolean closed = auctionMutations.executeWithLock(
                auction,
                Auction::closeIfEnded,
                (candidate, didClose) -> {
                    if (Boolean.TRUE.equals(didClose)) {
                        auctionPersistence.persistAuctionState(candidate);
                    }
                },
                null
        );
        if (closed) {
            LOGGER.info("Auction {} closed automatically with status {}.", auction.getId(), auction.getStatus());
            auctionBroadcaster.broadcastAuctionStatusChanged(auction);
        }
    }

    private void applyStateTransition(Auction auction, Consumer<Auction> transition) {
        auctionMutations.executeWithLock(auction, transition, auctionPersistence::persistAuctionState);
        auctionBroadcaster.broadcastAuctionStatusChanged(auction);
    }

    private void ensureAdminOrOwningSeller(User actor, Auction auction) {
        boolean isAdmin = actor.hasRole(UserRole.ADMIN);
        boolean isOwningSeller = Objects.equals(actor.getId(), auction.getSellerId());
        if (!isAdmin && !isOwningSeller) {
            throw new AuthorizationException("Only the seller or an admin can manage this auction.");
        }
    }
}
