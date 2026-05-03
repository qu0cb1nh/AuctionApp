package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionEndedMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.AuctionSummary;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.BidResultMessage;
import net.auctionapp.common.messages.types.BidView;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.messages.types.CreateItemResultMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.models.auction.Auction;
import net.auctionapp.common.models.auction.AuctionStatus;
import net.auctionapp.common.models.auction.BidTransaction;
import net.auctionapp.server.factories.ArtFactory;
import net.auctionapp.server.factories.ElectronicsFactory;
import net.auctionapp.common.models.items.Item;
import net.auctionapp.server.factories.ItemFactory;
import net.auctionapp.common.models.items.ItemType;
import net.auctionapp.server.factories.VehicleFactory;
import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.ServerApp;
import net.auctionapp.server.dao.AuctionDao;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.exceptions.AuthorizationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.exceptions.InvalidBidException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.exceptions.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Central in-memory manager for auction sessions.
 * Keeps the implementation small for the current project scope.
 */
public final class AuctionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionManager.class);
    private static AuctionManager instance;

    private final ConcurrentMap<String, Auction> auctions = new ConcurrentHashMap<>();
    private final ConcurrentSkipListSet<String> announcedEndedAuctionIds = new ConcurrentSkipListSet<>();
    private final AuthManager authManager;
    private final NotificationManager notificationManager;
    private volatile AuctionDao auctionDao;

    private AuctionManager() {
        this.authManager = AuthManager.getInstance();
        this.notificationManager = NotificationManager.getInstance();
    }

    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
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
            refreshAuctionStatuses();
            Auction auction = requireAuction(message.getAuctionId());
            AuctionDetailsResponseMessage response = buildAuctionDetailsResponse(auction);
            handler.sendResponse(response, message);
        } catch (AuctionAppException e) {
            handler.sendResponse(new ErrorMessage(e.getMessage()), message);
        }
    }

    public void handleCreateItem(CreateItemRequestMessage message, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            Item item = createItemFromRequest(message);
            String sellerId = StringUtil.normalizeString(handler.getAuthenticatedId());
            Auction auction = createAuction(
                    sellerId,
                    item,
                    message.getStartingPrice(),
                    message.getMinimumBidIncrement(),
                    message.getStartTime(),
                    message.getEndTime()
            );
            persistAuction(auction);
            handler.sendResponse(new CreateItemResultMessage(
                    auction.getId(),
                    auction.getItem().getTitle(),
                    "Auction created successfully."
            ), message);
        } catch (DatabaseException e) {
            handler.sendResponse(new ErrorMessage("Failed to save auction to database: " + e.getMessage()), message);
        } catch (AuctionAppException e) {
            handler.sendResponse(new ErrorMessage(e.getMessage()), message);
        }
    }

    public void handleBidRequest(BidRequestMessage message, ClientHandler handler) {
        String auctionId = message.getAuctionId();
        try {
            handler.ensureAuthenticated();
            String bidderId = StringUtil.normalizeString(handler.getAuthenticatedId());
            BidSubmissionResult bidSubmissionResult = submitBid(auctionId, bidderId, BigDecimal.valueOf(message.getPrice()));

            Auction updatedAuction = requireAuction(auctionId);
            sendBidAccepted(handler, message, updatedAuction);
            broadcastPriceUpdate(updatedAuction);
            trySendOutbidNotification(bidSubmissionResult.previousLeadingBidderId(), updatedAuction);
        } catch (AuctionAppException e) {
            sendBidRejected(handler, message, auctionId, e.getMessage());
        }
    }

    public void broadcastEndedAuctions() {
        refreshAuctionStatuses();
    }

    // --- Core Business Logic ---

    public List<Auction> getAllAuctions() {
        refreshAuctionStatuses();
        List<Auction> snapshots = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            snapshots.add(auction.snapshotCopy());
        }
        return snapshots;
    }

    public List<AuctionSummary> getAuctionSummaries() {
        refreshAuctionStatuses();
        List<AuctionSummary> result = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            synchronized (auction) {
                Item item = auction.getItem();
                result.add(new AuctionSummary(
                        auction.getId(),
                        item.getTitle(),
                        auction.getCurrentPrice(),
                        auction.getMinimumNextBid(),
                        auction.getStatus(),
                        auction.getLeadingBidderId(),
                        auction.getStartTime(),
                        auction.getEndTime()
                ));
            }
        }
        return result;
    }

    public Optional<Auction> getAuctionById(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return Optional.empty();
        }
        refreshAuctionStatuses();
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            return Optional.empty();
        }
        return Optional.of(auction.snapshotCopy());
    }

    public boolean addAuction(Auction auction) {
        if (auction == null || auction.getId() == null || auction.getId().isBlank()) {
            return false;
        }
        return auctions.putIfAbsent(auction.getId(), auction) == null;
    }

    public Auction createAuction(
            String sellerId,
            Item item,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        validateAuctionDraft(item, startingPrice, minimumBidIncrement, startTime, endTime);

        User seller = authManager.requireUserById(StringUtil.normalizeString(sellerId));
        Auction auction = new Auction(
                UUID.randomUUID().toString(),
                seller.getId(),
                startTime,
                endTime,
                item,
                startingPrice,
                minimumBidIncrement
        );

        auctions.put(auction.getId(), auction);
        return auction;
    }

    public Auction updateAuction(
            String sellerId,
            String auctionId,
            String title,
            String description,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        Auction auction = requireAuction(auctionId);
        ensureSellerOwnsAuction(sellerId, auction);
        ensureAuctionCanBeEdited(auction);

        boolean updated = auction.updateListingDetails(
                title,
                description,
                startingPrice,
                minimumBidIncrement,
                startTime,
                endTime
        );
        if (!updated) {
            throw new InvalidAuctionStateException("Auction draft update data is invalid.");
        }
        return auction;
    }

    public boolean removeAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return false;
        }
        return auctions.remove(auctionId) != null;
    }

    public void deleteAuction(String actorId, String auctionId) {
        Auction auction = requireAuction(auctionId);
        User actor = authManager.requireUserById(StringUtil.normalizeString(actorId));
        ensureAdminOrOwningSeller(actor, auction);

        LocalDateTime now = LocalDateTime.now();
        synchronized (auction) {
            boolean isRunningWindow = auction.getStatus() == AuctionStatus.RUNNING
                    && !now.isBefore(auction.getStartTime())
                    && now.isBefore(auction.getEndTime());
            if (isRunningWindow || auction.getStatus() == AuctionStatus.PAID) {
                throw new InvalidAuctionStateException("Running or paid auctions cannot be deleted.");
            }
        }

        auction.setStatus(AuctionStatus.CANCELED);
        auction.setWinnerBidderId(null);
        persistAuctionState(auction);
        broadcastAuctionStatusChanged(auction);
        auctions.remove(auctionId);
    }

    public boolean placeBid(String auctionId, BidTransaction bid) {
        Auction auction = auctions.get(auctionId);
        if (auction == null || bid == null) {
            return false;
        }
        if (!Objects.equals(auctionId, bid.getAuctionId())) {
            return false;
        }
        return auction.placeBid(bid, LocalDateTime.now());
    }

    public BidSubmissionResult submitBid(String auctionId, String bidderId, BigDecimal amount) {
        Auction auction = requireAuction(auctionId);
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        authManager.requireUserById(normalizedBidderId);
        validateBidAmount(amount);
        ensureBidderIsNotSeller(auction, normalizedBidderId);

        refreshAuctionStatuses();
        LocalDateTime now = LocalDateTime.now();
        ensureAuctionIsRunning(auction, now);
        BidTransaction bid = new BidTransaction(
                UUID.randomUUID().toString(),
                amount,
                now,
                normalizedBidderId,
                auctionId,
                false
        );

        String previousLeadingBidderId;
        synchronized (auction) {
            ensureAuctionIsRunning(auction, now);
            ensureBidAmountMeetsMinimum(auction, amount);
            previousLeadingBidderId = auction.getLeadingBidderId();
            if (!auction.placeBid(bid, now)) {
                throw new InvalidBidException("Bid was rejected by auction rules.");
            }
        }
        persistAuctionState(auction);
        return new BidSubmissionResult(bid, previousLeadingBidderId);
    }

    public Auction finishAuction(String auctionId) {
        Auction auction = requireAuction(auctionId);
        if (!settleAuctionNow(auction, LocalDateTime.now())) {
            throw new InvalidAuctionStateException("Auction cannot be settled before end time.");
        }
        persistAuctionState(auction);
        broadcastAuctionStatusChanged(auction);
        return auction;
    }

    public Auction markAuctionPaid(String auctionId) {
        Auction auction = requireAuction(auctionId);
        synchronized (auction) {
            if (auction.getStatus() != AuctionStatus.RUNNING) {
                throw new InvalidAuctionStateException("Only running auctions can be settled.");
            }
            if (LocalDateTime.now().isBefore(auction.getEndTime())) {
                throw new InvalidAuctionStateException("Auction cannot be settled before end time.");
            }
            if (auction.getLeadingBidderId() == null || auction.getLeadingBidderId().isBlank()) {
                throw new InvalidAuctionStateException("Cannot mark paid without a leading bidder.");
            }
            auction.setWinnerBidderId(auction.getLeadingBidderId());
            auction.setStatus(AuctionStatus.PAID);
        }
        persistAuctionState(auction);
        broadcastAuctionStatusChanged(auction);
        return auction;
    }

    public Auction cancelAuction(String actorId, String auctionId) {
        Auction auction = requireAuction(auctionId);
        User actor = authManager.requireUserById(StringUtil.normalizeString(actorId));
        ensureAdminOrOwningSeller(actor, auction);

        synchronized (auction) {
            if (auction.getStatus() == AuctionStatus.PAID) {
                throw new InvalidAuctionStateException("Paid auctions cannot be canceled.");
            }
            auction.setStatus(AuctionStatus.CANCELED);
            auction.setWinnerBidderId(null);
        }
        persistAuctionState(auction);
        broadcastAuctionStatusChanged(auction);
        return auction;
    }

    public void refreshAuctionStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctions.values()) {
            boolean statusChanged = settleAuctionNow(auction, now);
            if (statusChanged) {
                persistAuctionState(auction);
                broadcastAuctionStatusChanged(auction);
            }
        }
    }

    public void clear() {
        auctions.clear();
        announcedEndedAuctionIds.clear();
    }

    public synchronized void setAuctionDao(AuctionDao auctionDao) {
        this.auctionDao = auctionDao;
        if (auctionDao == null) {
            return;
        }
        List<Auction> persistedAuctions = auctionDao.findAllAuctions();
        auctions.clear();
        announcedEndedAuctionIds.clear();
        for (Auction auction : persistedAuctions) {
            auctions.put(auction.getId(), auction);
        }
    }

    public List<BidView> getBidViews(String auctionId) {
        Auction auction = requireAuction(auctionId);
        List<BidView> bidViews = new ArrayList<>();
        for (BidTransaction bid : auction.getBidHistory()) {
            bidViews.add(new BidView(bid.getId(), bid.getBidderId(), bid.getAmount(), bid.getTimestamp()));
        }
        return bidViews;
    }

    private Item createItemFromRequest(CreateItemRequestMessage message) {
        ItemType type = message.getItemType();
        if (type == null) {
            throw new AuctionAppException("Item type is required.");
        }
        ItemFactory factory = switch (type) {
            case ART -> new ArtFactory();
            case ELECTRONICS -> new ElectronicsFactory();
            case VEHICLE -> new VehicleFactory();
        };
        return factory.createItem(message);
    }

    private void persistAuction(Auction auction) {
        if (auctionDao == null) {
            throw new AuctionAppException("Auction persistence is not configured.");
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
        throw new AuctionAppException("Auction could not be persisted.");
    }

    private void persistAuctionState(Auction auction) {
        if (auctionDao == null) {
            return;
        }
        try {
            if (auctionDao.updateAuctionState(auction)) {
                return;
            }
        } catch (DatabaseException e) {
            throw new AuctionAppException("Failed to persist auction state.");
        }
        throw new AuctionAppException("Auction state could not be persisted.");
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

    private void ensureSellerOwnsAuction(String sellerId, Auction auction) {
        User seller = authManager.requireUserById(StringUtil.normalizeString(sellerId));
        if (!Objects.equals(seller.getId(), auction.getSellerId())) {
            throw new AuthorizationException("Only the owning seller can modify this auction.");
        }
    }

    private void ensureAdminOrOwningSeller(User actor, Auction auction) {
        boolean isAdmin = actor.hasRole(UserRole.ADMIN);
        boolean isOwningSeller = Objects.equals(actor.getId(), auction.getSellerId());
        if (!isAdmin && !isOwningSeller) {
            throw new AuthorizationException("Only the seller or an admin can manage this auction.");
        }
    }

    private void validateBidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Bid amount must be greater than zero.");
        }
    }

    private void ensureBidderIsNotSeller(Auction auction, String bidderId) {
        if (Objects.equals(auction.getSellerId(), bidderId)) {
            throw new InvalidBidException("Seller cannot bid on own auction.");
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

    private void ensureAuctionCanBeEdited(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        synchronized (auction) {
            if (auction.getStatus() != AuctionStatus.RUNNING
                    || !now.isBefore(auction.getStartTime())) {
                throw new InvalidAuctionStateException("Only OPEN auctions can be edited.");
            }
        }
    }

    private boolean settleAuctionNow(Auction auction, LocalDateTime now) {
        synchronized (auction) {
            if (auction.getStatus() != AuctionStatus.RUNNING
                    || now == null
                    || now.isBefore(auction.getEndTime())) {
                return false;
            }
            String leadingBidderId = StringUtil.normalizeString(auction.getLeadingBidderId());
            if (leadingBidderId.isEmpty()) {
                auction.setWinnerBidderId(null);
                auction.setStatus(AuctionStatus.CANCELED);
            } else {
                auction.setWinnerBidderId(leadingBidderId);
                auction.setStatus(AuctionStatus.PAID);
            }
            return true;
        }
    }

    private void broadcastAuctionStatusChanged(Auction auction) {
        if (auction == null) {
            return;
        }
        AuctionStatus status = auction.getStatus();
        if (status != AuctionStatus.PAID && status != AuctionStatus.CANCELED) {
            return;
        }
        if (!announcedEndedAuctionIds.add(auction.getId())) {
            return;
        }
        ServerApp.broadcast(JsonUtil.toJson(new AuctionEndedMessage(
                auction.getId(),
                auction.getWinnerBidderId(),
                auction.getCurrentPrice()
        )));
    }

    private void ensureBidAmountMeetsMinimum(Auction auction, BigDecimal amount) {
        BigDecimal minimumNextBid = auction.getMinimumNextBid();
        if (minimumNextBid != null && amount.compareTo(minimumNextBid) < 0) {
            throw new InvalidBidException("Bid must be at least " + minimumNextBid + ".");
        }
    }

    private void sendBidAccepted(ClientHandler handler, BidRequestMessage request, Auction auction) {
        synchronized (auction) {
            handler.sendResponse(new BidResultMessage(
                    MessageType.BID_ACCEPTED,
                    auction.getId(),
                    auction.getCurrentPrice(),
                    auction.getLeadingBidderId(),
                    "Bid accepted."
            ), request);
        }
    }

    private void sendBidRejected(ClientHandler handler, BidRequestMessage request, String auctionId, String message) {
        handler.sendResponse(new BidResultMessage(
                MessageType.BID_REJECTED,
                auctionId,
                null,
                null,
                message
        ), request);
    }

    private void broadcastPriceUpdate(Auction auction) {
        synchronized (auction) {
            ServerApp.broadcast(JsonUtil.toJson(new PriceUpdateMessage(
                    auction.getId(),
                    auction.getCurrentPrice().doubleValue(),
                    auction.getLeadingBidderId()
            )));
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
            notificationManager.sendOutbidNotification(
                    displacedBidderId,
                    updatedAuction.getId(),
                    updatedAuction.getItem().getTitle(),
                    updatedAuction.getCurrentPrice(),
                    newLeadingBidderId
            );
        } catch (AuctionAppException e) {
            LOGGER.warn("Failed to send outbid notification for auction {}: {}", updatedAuction.getId(), e.getMessage());
        }
    }

    private AuctionDetailsResponseMessage buildAuctionDetailsResponse(Auction auction) {
        synchronized (auction) {
            Item item = auction.getItem();
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
                    getBidViews(auction.getId())
            );
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

    public record BidSubmissionResult(BidTransaction bidTransaction, String previousLeadingBidderId) {
    }
}
