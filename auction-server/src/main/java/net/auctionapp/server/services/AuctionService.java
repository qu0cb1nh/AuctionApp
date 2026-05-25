package net.auctionapp.server.services;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.dto.AuctionSummary;
import net.auctionapp.common.dto.BidView;
import net.auctionapp.common.messages.auction.AuctionActionResponseMessage;
import net.auctionapp.common.messages.auction.AuctionDetailsListResponseMessage;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.auction.AuctionEndedResponseMessage;
import net.auctionapp.common.messages.auction.AuctionListResponseMessage;
import net.auctionapp.common.messages.auction.BidRequestMessage;
import net.auctionapp.common.messages.auction.BidResponseMessage;
import net.auctionapp.common.messages.auction.CancelAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CloseAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemResponseMessage;
import net.auctionapp.common.messages.auction.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.auction.GetMyActivityRequestMessage;
import net.auctionapp.common.messages.auction.GetMyListingsRequestMessage;
import net.auctionapp.common.messages.auction.ObserveAuctionRequestMessage;
import net.auctionapp.common.messages.auction.PriceUpdateResponseMessage;
import net.auctionapp.common.messages.auction.UpdateAuctionRequestMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.factories.ItemFactories;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.factories.ItemFactory;
import net.auctionapp.server.models.users.User;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.MoneyUtil;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.AuctionDao;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.AuthorizationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.ImageStorageException;
import net.auctionapp.server.exceptions.InsufficientFundsException;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.exceptions.InvalidBidException;
import net.auctionapp.server.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Central in-memory manager for auction sessions.
 * Keeps the implementation small for the current project scope.
 */
public final class AuctionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionService.class);
    private static AuctionService instance;

    private final ConcurrentMap<String, Auction> auctions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<ClientHandler>> auctionSubscribers = new ConcurrentHashMap<>();
    private final ReentrantLock auctionMutationLock = new ReentrantLock();
    private final AuthService authService;
    private final NotificationService notificationService;
    private final CloudinaryImageService cloudinaryImageService;
    private final WalletService walletService;
    private final WatchListService watchListService;
    private volatile AuctionDao auctionDao;
    private volatile ScheduledExecutorService statusScheduler;

    private AuctionService() {
        this.authService = AuthService.getInstance();
        this.notificationService = NotificationService.getInstance();
        this.cloudinaryImageService = CloudinaryImageService.getInstance();
        this.walletService = WalletService.getInstance();
        this.watchListService = WatchListService.getInstance();
    }

    public static synchronized AuctionService getInstance() {
        if (instance == null) {
            instance = new AuctionService();
        }
        return instance;
    }

    // --- Message Handling Logic ---

    public void handleGetAuctionList(GetAuctionListRequestMessage request, ClientHandler handler) {
        handler.sendResponse(new AuctionListResponseMessage(getAuctionSummaries()), request);
    }

    public void handleGetAuctionDetails(GetAuctionDetailsRequestMessage message, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            Auction auction = requireAuction(message.getAuctionId());
            AuctionDetailsResponseMessage response = buildAuctionDetailsResponse(auction);
            handler.sendResponse(response, message);
        } catch (AuthenticationException | NotFoundException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), message);
        }
    }

    public void handleGetMyActivity(GetMyActivityRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = StringUtil.normalizeString(handler.getAuthenticatedId());
            List<AuctionDetailsResponseMessage> activity = new ArrayList<>();
            for (Auction auction : auctions.values()) {
                boolean hasBid = auction.getActiveBidHistory().stream()
                        .anyMatch(bid -> userId.equals(StringUtil.normalizeString(bid.getBidderId())));
                if (hasBid) {
                    activity.add(buildAuctionDetailsResponse(auction));
                }
            }
            handler.sendResponse(new AuctionDetailsListResponseMessage(activity), request);
        } catch (AuthenticationException | NotFoundException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), request);
        }
    }

    public void handleGetMyListings(GetMyListingsRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = StringUtil.normalizeString(handler.getAuthenticatedId());
            List<AuctionDetailsResponseMessage> listings = new ArrayList<>();
            for (Auction auction : auctions.values()) {
                if (userId.equals(StringUtil.normalizeString(auction.getSellerId()))) {
                    listings.add(buildAuctionDetailsResponse(auction));
                }
            }
            handler.sendResponse(new AuctionDetailsListResponseMessage(listings), request);
        } catch (AuthenticationException | NotFoundException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), request);
        }
    }

    public void handleObserveAuction(ObserveAuctionRequestMessage message, ClientHandler handler) {
        if (!message.isObserving()) {
            removeSubscriber(message.getAuctionId(), handler);
            return;
        }
        try {
            handler.ensureAuthenticated();
            Auction auction = requireAuction(message.getAuctionId());
            auctionSubscribers.computeIfAbsent(auction.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(handler);
        } catch (AuthenticationException | NotFoundException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), message);
        }
    }

    public void removeSubscriber(ClientHandler handler) {
        if (handler == null) {
            return;
        }
        for (String auctionId : auctionSubscribers.keySet()) {
            removeSubscriber(auctionId, handler);
        }
    }

    public void handleCreateItem(CreateItemRequestMessage message, ClientHandler handler) {
        CloudinaryImageService.UploadedImage uploadedImage = null;
        try {
            handler.ensureAuthenticated();
            Item item = createItemFromRequest(message);
            validateAuctionDraft(
                    item,
                    message.getStartingPrice(),
                    message.getMinimumBidIncrement(),
                    message.getStartTime(),
                    message.getEndTime()
            );
            uploadedImage = cloudinaryImageService.uploadAuctionItemImage(message);
            item.setImageUrl(uploadedImage == null ? null : uploadedImage.url());
            String sellerId = StringUtil.normalizeString(handler.getAuthenticatedId());
            Auction auction = openAuction(
                    sellerId,
                    item,
                    message.getStartingPrice(),
                    message.getMinimumBidIncrement(),
                    message.getStartTime(),
                    message.getEndTime()
            );
            handler.sendResponse(new CreateItemResponseMessage(
                    auction.getId(),
                    auction.getItem().getTitle(),
                    auction.getItem().getImageUrl(),
                    "Auction created successfully."
            ), message);
        } catch (DatabaseException e) {
            cloudinaryImageService.deleteAuctionItemImage(uploadedImage);
            LOGGER.warn("Auction creation persistence failed: {}", e.getMessage(), e);
            handler.sendResponse(new ErrorResponseMessage("Unable to save auction."), message);
        } catch (ImageStorageException e) {
            cloudinaryImageService.deleteAuctionItemImage(uploadedImage);
            LOGGER.warn("Auction image upload failed: {}", e.getMessage(), e);
            handler.sendResponse(new ErrorResponseMessage("Unable to store auction image."), message);
        } catch (AuthenticationException | InvalidAuctionStateException | NotFoundException | ValidationException e) {
            cloudinaryImageService.deleteAuctionItemImage(uploadedImage);
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), message);
        } catch (RuntimeException e) {
            cloudinaryImageService.deleteAuctionItemImage(uploadedImage);
            LOGGER.warn("Auction creation failed unexpectedly: {}", e.getMessage(), e);
            handler.sendResponse(new ErrorResponseMessage("Failed to create auction."), message);
        }
    }

    public void handleBidRequest(BidRequestMessage message, ClientHandler handler) {
        String auctionId = message.getAuctionId();
        try {
            handler.ensureAuthenticated();
            String bidderId = StringUtil.normalizeString(handler.getAuthenticatedId());
            String previousLeadingBidderId = submitBid(auctionId, bidderId, message.getPrice());

            Auction updatedAuction = requireAuction(auctionId);
            sendBidAccepted(handler, message, updatedAuction);
            broadcastPriceUpdate(updatedAuction);
            trySendOutbidNotification(previousLeadingBidderId, updatedAuction);
        } catch (AuthenticationException | InvalidAuctionStateException | InvalidBidException | InsufficientFundsException
                 | NotFoundException | ValidationException e) {
            sendBidRejected(handler, message, auctionId, e.getMessage());
        } catch (DatabaseException e) {
            LOGGER.warn("Bid persistence failed for auction {}: {}", auctionId, e.getMessage(), e);
            sendBidRejected(handler, message, auctionId, "Unable to process bid.");
        } catch (RuntimeException e) {
            LOGGER.warn("Bid processing failed unexpectedly for auction {}: {}", auctionId, e.getMessage(), e);
            sendBidRejected(handler, message, auctionId, "Unable to process bid.");
        }
    }

    public void handleUpdateAuction(UpdateAuctionRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            updateAuction(
                    actorId,
                    request.getAuctionId(),
                    request.getTitle(),
                    request.getDescription(),
                    request.getStartingPrice(),
                    request.getMinimumBidIncrement(),
                    request.getStartTime(),
                    request.getEndTime()
            );
            handler.sendResponse(new AuctionActionResponseMessage("Auction updated successfully."), request);
        } catch (RuntimeException e) {
            sendAuctionActionError(handler, request, e);
        }
    }

    public void handleCancelAuction(CancelAuctionRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            cancelAuction(actorId, request.getAuctionId());
            handler.sendResponse(new AuctionActionResponseMessage("Auction canceled successfully."), request);
        } catch (RuntimeException e) {
            sendAuctionActionError(handler, request, e);
        }
    }

    public void handleCloseAuction(CloseAuctionRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            closeAuction(actorId, request.getAuctionId());
            handler.sendResponse(new AuctionActionResponseMessage("Auction closed successfully."), request);
        } catch (RuntimeException e) {
            sendAuctionActionError(handler, request, e);
        }
    }

    public synchronized void startStatusScheduler() {
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
                refreshAuctionStatuses();
            } catch (RuntimeException e) {
                LOGGER.warn("Auction status refresh failed: {}", e.getMessage(), e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void stopStatusScheduler() {
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            statusScheduler = null;
        }
    }

    // --- Core Business Logic ---

    private List<AuctionSummary> getAuctionSummaries() {
        List<AuctionSummary> result = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            result.add(buildAuctionSummary(auction));
        }
        return result;
    }

    List<AuctionSummary> getAuctionSummaries(Iterable<String> auctionIds) {
        List<AuctionSummary> result = new ArrayList<>();
        if (auctionIds == null) {
            return result;
        }
        for (String auctionId : auctionIds) {
            Auction auction = auctions.get(auctionId);
            if (auction != null) {
                result.add(buildAuctionSummary(auction));
            }
        }
        return result;
    }

    boolean hasAuction(String auctionId) {
        return auctionId != null && auctions.containsKey(auctionId);
    }

    private AuctionSummary buildAuctionSummary(Auction auction) {
        synchronized (auction) {
            Item item = auction.getItem();
            return new AuctionSummary(
                    auction.getId(),
                    item.getTitle(),
                    auction.getCurrentPrice(),
                    auction.getMinimumNextBid(),
                    auction.getStatus(),
                    auction.getLeadingBidderId(),
                    auction.getStartTime(),
                    auction.getEndTime(),
                    item.getImageUrl(),
                    item.getType(),
                    displayUsername(auction.getLeadingBidderId()),
                    auction.getSellerId(),
                    displayUsername(auction.getSellerId())
            );
        }
    }

    private Auction openAuction(
            String sellerId,
            Item item,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        validateAuctionDraft(item, startingPrice, minimumBidIncrement, startTime, endTime);

        auctionMutationLock.lock();
        try {
            User seller = authService.requireActiveUserById(StringUtil.normalizeString(sellerId));
            Auction auction = new Auction(
                    UUID.randomUUID().toString(),
                    seller.getId(),
                    startTime,
                    endTime,
                    item,
                    startingPrice,
                    minimumBidIncrement
            );

            if (auction.getStatus() != AuctionStatus.RUNNING || auctions.putIfAbsent(auction.getId(), auction) != null) {
                throw new InvalidAuctionStateException("Auction is already open.");
            }
            try {
                persistAuction(auction);
            } catch (RuntimeException e) {
                auctions.remove(auction.getId(), auction);
                throw e;
            }
            return auction;
        } finally {
            auctionMutationLock.unlock();
        }
    }

    private Auction updateAuction(
            String actorId,
            String auctionId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        Auction auction = requireAuction(auctionId);
        auctionMutationLock.lock();
        try {
            User actor = authService.requireActiveUserById(StringUtil.normalizeString(actorId));
            ensureAdminOrOwningSeller(actor, auction);

            synchronized (auction) {
                if (auction.getStatus() != AuctionStatus.RUNNING) {
                    throw new InvalidAuctionStateException("Only active auctions can be edited.");
                }
                Auction candidate = auction.snapshotCopy();
                boolean updated = candidate.updateListingDetails(
                        title,
                        description,
                        startingPrice,
                        minimumBidIncrement,
                        startTime,
                        endTime,
                        LocalDateTime.now()
                );
                if (!updated) {
                    throw new InvalidAuctionStateException("Auction draft update data is invalid.");
                }
                persistAuctionDetails(candidate);
                auction.applySnapshot(candidate);
            }
        } finally {
            auctionMutationLock.unlock();
        }
        return auction;
    }

    private String submitBid(String auctionId, String bidderId, BigDecimal amount) {
        Auction auction = requireAuction(auctionId);
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        MoneyUtil.requirePositiveMoney(amount, "Bid amount");
        if (Objects.equals(auction.getSellerId(), normalizedBidderId)) {
            throw new InvalidBidException("Seller cannot bid on own auction.");
        }

        String previousLeadingBidderId;
        auctionMutationLock.lock();
        try {
            authService.requireActiveUserById(normalizedBidderId);
            LocalDateTime now = LocalDateTime.now();
            closeAuctionIfEnded(auction, now);
            ensureAuctionIsRunning(auction, now);

            BidTransaction bid = new BidTransaction(
                    UUID.randomUUID().toString(),
                    amount,
                    now,
                    normalizedBidderId,
                    auctionId
            );

            synchronized (auction) {
                ensureAuctionIsRunning(auction, now);
                BigDecimal minimumNextBid = auction.getMinimumNextBid();
                if (minimumNextBid != null && amount.compareTo(minimumNextBid) < 0) {
                    throw new InvalidBidException("Bid must be at least " + minimumNextBid + ".");
                }

                previousLeadingBidderId = auction.getLeadingBidderId();
                BigDecimal existingCommitment = walletService.getBidderCommitment(auction, normalizedBidderId);
                BigDecimal incrementalAmount = amount.subtract(existingCommitment);

                if (incrementalAmount.signum() <= 0) {
                    throw new InvalidBidException("Your new bid must be higher than your existing commitment.");
                }

                Auction candidate = auction.snapshotCopy();
                if (!candidate.placeBid(bid, now)) {
                    throw new InvalidBidException("Bid was rejected by auction rules.");
                }
                persistAcceptedBid(candidate, bid, incrementalAmount);
                auction.applySnapshot(candidate);
                walletService.applyLockedBidFunds(normalizedBidderId, incrementalAmount);
            }
        } finally {
            auctionMutationLock.unlock();
        }

        return previousLeadingBidderId;
    }

    public void applyUserBanEffects(String bannedUserId) {
        String normalizedBannedUserId = StringUtil.normalizeString(bannedUserId);
        if (normalizedBannedUserId.isEmpty()) {
            throw new ValidationException("User not found.");
        }

        List<BanAuctionChange> changes = new ArrayList<>();
        List<BidTransaction> invalidatedBids = new ArrayList<>();
        Map<String, BigDecimal> fundsToRelease = new HashMap<>();
        auctionMutationLock.lock();
        try {
            for (Auction auction : auctions.values()) {
                synchronized (auction) {
                    if (auction.getStatus() != AuctionStatus.RUNNING) {
                        continue;
                    }
                    Auction candidate = auction.snapshotCopy();
                    if (normalizedBannedUserId.equals(StringUtil.normalizeString(candidate.getSellerId()))) {
                        candidate.setWinnerBidderId(null);
                        candidate.setStatus(AuctionStatus.CANCELED);
                        mergeFundsToRelease(fundsToRelease, walletService.getCommittedAmountsByBidder(candidate));
                        changes.add(new BanAuctionChange(auction, candidate, true, false));
                        continue;
                    }

                    String previousLeaderId = StringUtil.normalizeString(candidate.getLeadingBidderId());
                    BigDecimal bidderCommitment = walletService.getBidderCommitment(candidate, normalizedBannedUserId);
                    List<BidTransaction> newlyInvalidatedBids = candidate.invalidateActiveBidsBy(normalizedBannedUserId);
                    if (newlyInvalidatedBids.isEmpty()) {
                        continue;
                    }
                    for (BidTransaction invalidatedBid : newlyInvalidatedBids) {
                        if (!isRestoredBid(invalidatedBid)) {
                            invalidatedBids.add(invalidatedBid);
                        }
                    }
                    addFundsToRelease(fundsToRelease, normalizedBannedUserId, bidderCommitment);
                    boolean leadingBidderChanged = !previousLeaderId.equals(
                            StringUtil.normalizeString(candidate.getLeadingBidderId()));
                    changes.add(new BanAuctionChange(auction, candidate, false, leadingBidderChanged));
                }
            }

            List<Auction> changedAuctions = changes.stream()
                    .map(BanAuctionChange::updatedAuction)
                    .toList();
            persistUserBanEffects(normalizedBannedUserId, changedAuctions, invalidatedBids, fundsToRelease);
            for (BanAuctionChange change : changes) {
                change.originalAuction().applySnapshot(change.updatedAuction());
            }
            walletService.applyReleasedFunds(fundsToRelease);
            authService.applyPersistedUserBanStatus(authService.requireUserById(normalizedBannedUserId), true);
        } finally {
            auctionMutationLock.unlock();
        }

        for (BanAuctionChange change : changes) {
            if (change.sellerAuctionCanceled()) {
                broadcastAuctionStatusChanged(change.originalAuction());
            } else {
                broadcastPriceUpdate(change.originalAuction());
                if (change.leadingBidderChanged()) {
                    trySendBidRemovalNotifications(change.originalAuction());
                }
            }
        }
    }

    private void closeAuction(String actorId, String auctionId) {
        Auction auction = requireAuction(auctionId);
        User actor = authService.requireUserById(StringUtil.normalizeString(actorId));
        ensureAdminOrOwningSeller(actor, auction);
        closeAuctionManually(auction);
    }

    private void cancelAuction(String actorId, String auctionId) {
        Auction auction = requireAuction(auctionId);
        User actor = authService.requireUserById(StringUtil.normalizeString(actorId));
        ensureAdminOrOwningSeller(actor, auction);
        boolean actorIsAdmin = actor.hasRole(UserRole.ADMIN);
        if (!finishAuction(auction, true, true, () -> {
            if (!actorIsAdmin && !auction.getBidHistory().isEmpty()) {
                throw new AuthorizationException("Only an admin can cancel auctions that already have bids.");
            }
        })) {
            throw new InvalidAuctionStateException("Auction is already closed.");
        }
    }

    private void closeAuctionManually(Auction auction) {
        synchronized (auction) {
            if (auction.getStatus() != AuctionStatus.RUNNING) {
                throw new InvalidAuctionStateException("Auction is already closed.");
            }
        }
        if (!finishAuction(auction, false, true)) {
            throw new InvalidAuctionStateException("Auction is already closed.");
        }
    }

    private boolean finishAuction(Auction auction, boolean forceCancel, boolean shouldBroadcastAuctionEnded) {
        return finishAuction(auction, forceCancel, shouldBroadcastAuctionEnded, null);
    }

    private boolean finishAuction(
            Auction auction,
            boolean forceCancel,
            boolean shouldBroadcastAuctionEnded,
            Runnable preCloseValidation
    ) {
        auctionMutationLock.lock();
        try {
            synchronized (auction) {
                if (auction.getStatus() != AuctionStatus.RUNNING) {
                    return false;
                }
                if (preCloseValidation != null) {
                    preCloseValidation.run();
                }

                String leadingBidderId = StringUtil.normalizeString(auction.getLeadingBidderId());
                boolean hasWinner = !forceCancel && !leadingBidderId.isEmpty();
                Auction candidate = auction.snapshotCopy();
                candidate.setWinnerBidderId(hasWinner ? leadingBidderId : null);
                candidate.setStatus(hasWinner ? AuctionStatus.PAID : AuctionStatus.CANCELED);

                persistAuctionState(candidate);
                auction.applySnapshot(candidate);
            }
        } finally {
            auctionMutationLock.unlock();
        }
        if (shouldBroadcastAuctionEnded) {
            broadcastAuctionStatusChanged(auction);
        }
        return true;
    }

    public void refreshAuctionStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctions.values()) {
            try {
                watchListService.sendEndingSoonReminders(buildAuctionSummary(auction), now);
                closeAuctionIfEnded(auction, now);
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to refresh auction {}: {}", auction.getId(), e.getMessage(), e);
            }
        }
    }

    public synchronized void setAuctionDao(AuctionDao auctionDao) {
        this.auctionDao = auctionDao;
        if (auctionDao == null) {
            return;
        }
        List<Auction> persistedAuctions = auctionDao.findAllAuctions();
        auctions.clear();
        for (Auction auction : persistedAuctions) {
            auctions.put(auction.getId(), auction);
        }
    }

    private Item createItemFromRequest(CreateItemRequestMessage message) {
        if (message.getItemType() == null) {
            throw new ValidationException("Item type is required.");
        }
        ItemFactory factory = ItemFactories.forType(message.getItemType());
        return factory.createItem(message);
    }

    private void persistAuction(Auction auction) {
        if (auctionDao == null) {
            throw new DatabaseException("Auction persistence is not configured.");
        }
        try {
            if (auctionDao.createAuction(auction)) {
                return;
            }
        } catch (DatabaseException e) {
            auctions.remove(auction.getId());
            throw e;
        }
        auctions.remove(auction.getId());
        throw new DatabaseException("Auction could not be persisted.");
    }

    private void persistAuctionState(Auction auction) {
        walletService.closeAuctionWallets(auction);
    }

    private void persistAcceptedBid(Auction auction, BidTransaction bid, BigDecimal amountToLock) {
        if (auctionDao == null) {
            throw new DatabaseException("Auction persistence is not configured.");
        }
        try {
            if (auctionDao.recordBid(auction, bid, amountToLock)) {
                return;
            }
        } catch (DatabaseException e) {
            throw new DatabaseException("Failed to persist bid.", e);
        }
        throw new InsufficientFundsException("Insufficient balance. Please check your wallet.");
    }

    private void persistAuctionDetails(Auction auction) {
        if (auctionDao == null) {
            return;
        }
        try {
            if (auctionDao.updateAuction(auction)) {
                return;
            }
        } catch (DatabaseException e) {
            throw new DatabaseException("Failed to persist auction details.", e);
        }
        throw new DatabaseException("Auction details could not be persisted.");
    }

    private void persistUserBanEffects(
            String bannedUserId,
            List<Auction> changedAuctions,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        if (auctionDao == null) {
            throw new DatabaseException("Auction persistence is not configured.");
        }
        try {
            if (auctionDao.applyUserBanEffects(bannedUserId, changedAuctions, invalidatedBids, fundsToRelease)) {
                return;
            }
        } catch (DatabaseException e) {
            throw new DatabaseException("Failed to persist user ban effects.", e);
        }
        throw new DatabaseException("User ban effects could not be persisted.");
    }

    private void mergeFundsToRelease(
            Map<String, BigDecimal> fundsToRelease,
            Map<String, BigDecimal> auctionCommitments
    ) {
        if (auctionCommitments == null) {
            return;
        }
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

    private boolean isRestoredBid(BidTransaction bid) {
        return bid != null && ("restored-" + bid.getAuctionId()).equals(bid.getId());
    }

    private Auction requireAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            throw new NotFoundException("Auction not found.");
        }
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new NotFoundException("Auction not found.");
        }
        return auction;
    }

    private void ensureAdminOrOwningSeller(User actor, Auction auction) {
        boolean isAdmin = actor.hasRole(UserRole.ADMIN);
        boolean isOwningSeller = Objects.equals(actor.getId(), auction.getSellerId());
        if (!isAdmin && !isOwningSeller) {
            throw new AuthorizationException("Only the seller or an admin can manage this auction.");
        }
    }

    private void ensureAuctionIsRunning(Auction auction, LocalDateTime now) {
        synchronized (auction) {
            if (auction.getStatus() != AuctionStatus.RUNNING
                    || now == null
                    || now.isBefore(auction.getStartTime())
                    || !now.isBefore(auction.getEndTime())) {
                throw new InvalidAuctionStateException("Auction is not open for bidding.");
            }
        }
    }

    private void closeAuctionIfEnded(Auction auction, LocalDateTime now) {
        synchronized (auction) {
            if (auction.getStatus() != AuctionStatus.RUNNING
                    || now == null
                    || now.isBefore(auction.getEndTime())) {
                return;
            }
        }
        finishAuction(auction, false, true);
    }

    private void broadcastAuctionStatusChanged(Auction auction) {
        if (auction == null) {
            return;
        }
        AuctionStatus status = auction.getStatus();
        if (status != AuctionStatus.PAID && status != AuctionStatus.CANCELED) {
            return;
        }
        trySendAuctionEndedNotifications(auction);
        sendToSubscribers(auction.getId(), new AuctionEndedResponseMessage(
                auction.getId(),
                auction.getWinnerBidderId(),
                auction.getCurrentPrice()
        ));
    }

    private void sendBidAccepted(ClientHandler handler, BidRequestMessage request, Auction auction) {
        synchronized (auction) {
            handler.sendResponse(new BidResponseMessage(
                    MessageType.BID_ACCEPTED,
                    auction.getId(),
                    auction.getCurrentPrice(),
                    auction.getLeadingBidderId(),
                    auction.getEndTime(),
                    "Bid accepted."
            ), request);
        }
    }

    private void sendBidRejected(
            ClientHandler handler,
            BidRequestMessage request,
            String auctionId,
            String message
    ) {
        handler.sendResponse(new BidResponseMessage(
                MessageType.BID_REJECTED,
                auctionId,
                null,
                null,
                message
        ), request);
    }

    private void sendAuctionActionError(ClientHandler handler, Message request, RuntimeException e) {
        if (e instanceof AuthenticationException
                || e instanceof AuthorizationException
                || e instanceof InvalidAuctionStateException
                || e instanceof NotFoundException
                || e instanceof ValidationException) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), request);
            return;
        }
        if (e instanceof DatabaseException) {
            LOGGER.warn("Auction action persistence failed: {}", e.getMessage(), e);
        } else {
            LOGGER.warn("Auction action failed unexpectedly: {}", e.getMessage(), e);
        }
        handler.sendResponse(new ErrorResponseMessage("Auction request failed."), request);
    }

    private void broadcastPriceUpdate(Auction auction) {
        synchronized (auction) {
            sendToSubscribers(auction.getId(), new PriceUpdateResponseMessage(
                    auction.getId(),
                    auction.getCurrentPrice(),
                    displayUsername(auction.getLeadingBidderId()),
                    auction.getEndTime()
            ));
        }
    }

    private void sendToSubscribers(String auctionId, Message message) {
        Set<ClientHandler> subscribers = auctionSubscribers.get(auctionId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (ClientHandler subscriber : subscribers) {
            if (!subscriber.sendMessage(message)) {
                removeSubscriber(auctionId, subscriber);
            }
        }
    }

    private void removeSubscriber(String auctionId, ClientHandler handler) {
        if (auctionId == null || auctionId.isBlank() || handler == null) {
            return;
        }
        Set<ClientHandler> subscribers = auctionSubscribers.get(auctionId);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(handler);
        if (subscribers.isEmpty()) {
            auctionSubscribers.remove(auctionId, subscribers);
        }
    }

    private void trySendOutbidNotification(String previousLeadingBidderId, Auction updatedAuction) {
        if (updatedAuction == null) {
            return;
        }
        String displacedBidderId = StringUtil.normalizeString(previousLeadingBidderId);
        String newLeadingBidderId = StringUtil.normalizeString(updatedAuction.getLeadingBidderId());
        if (displacedBidderId.isEmpty() || newLeadingBidderId.isEmpty() || displacedBidderId.equals(newLeadingBidderId)) {
            return;
        }
        try {
            notificationService.sendOutbidNotification(
                    displacedBidderId,
                    updatedAuction.getId(),
                    updatedAuction.getItem().getTitle(),
                    updatedAuction.getCurrentPrice(),
                    newLeadingBidderId
            );
        } catch (DatabaseException e) {
            LOGGER.warn("Failed to send outbid notification for auction {}: {}", updatedAuction.getId(), e.getMessage());
        }
    }

    private void trySendAuctionEndedNotifications(Auction auction) {
        if (auction == null) {
            return;
        }
        try {
            notificationService.sendAuctionEndedNotifications(
                    auction.getId(),
                    auction.getItem() == null ? null : auction.getItem().getTitle(),
                    auction.getSellerId(),
                    auction.getWinnerBidderId(),
                    auction.getCurrentPrice()
            );
        } catch (DatabaseException e) {
            LOGGER.warn("Failed to send auction-ended notifications for {}: {}", auction.getId(), e.getMessage());
        }
    }

    private void trySendBidRemovalNotifications(Auction auction) {
        if (auction == null) {
            return;
        }
        try {
            notificationService.sendBidRemovalNotifications(
                    auction.getId(),
                    auction.getItem() == null ? null : auction.getItem().getTitle(),
                    auction.getSellerId(),
                    auction.getLeadingBidderId()
            );
        } catch (DatabaseException e) {
            LOGGER.warn("Failed to send bid-removal notifications for {}: {}", auction.getId(), e.getMessage());
        }
    }

    private AuctionDetailsResponseMessage buildAuctionDetailsResponse(Auction auction) {
        synchronized (auction) {
            Item item = auction.getItem();
            List<BidView> bidViews = new ArrayList<>();
            for (BidTransaction bid : auction.getActiveBidHistory()) {
                bidViews.add(new BidView(bid.getId(), bid.getBidderId(), bid.getAmount(), bid.getTimestamp()));
            }
            return new AuctionDetailsResponseMessage(
                    auction.getId(),
                    auction.getSellerId(),
                    item.getTitle(),
                    item.getDescription(),
                    auction.getStartingPrice(),
                    auction.getCurrentPrice(),
                    auction.getMinimumNextBid(),
                    auction.getStatus(),
                    auction.getLeadingBidderId(),
                    auction.getWinnerBidderId(),
                    auction.getStartTime(),
                    auction.getEndTime(),
                    item.getImageUrl(),
                    item.getType(),
                    bidViews,
                    displayUsername(auction.getLeadingBidderId()),
                    displayUsername(auction.getWinnerBidderId()),
                    displayUsername(auction.getSellerId())
            );
        }
    }

    private String displayUsername(String userId) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        if (normalizedUserId.isEmpty()) {
            return null;
        }
        try {
            return authService.requireUserById(normalizedUserId).getUsername();
        } catch (NotFoundException | DatabaseException e) {
            return normalizedUserId;
        }
    }

    private void validateAuctionDraft(
            Item item,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (item == null) {
            throw new ValidationException("Item is required.");
        }
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new ValidationException("Item title is required.");
        }
        if (item.getDescription() == null || item.getDescription().isBlank()) {
            throw new ValidationException("Item description is required.");
        }
        if (startingPrice == null || startingPrice.signum() < 0) {
            throw new ValidationException("Starting price must not be negative.");
        }
        if (minimumBidIncrement == null || minimumBidIncrement.signum() <= 0) {
            throw new ValidationException("Minimum bid increment must be greater than zero.");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ValidationException("Auction end time must be after start time.");
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
