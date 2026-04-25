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
import net.auctionapp.common.utils.UserIdentityUtil;
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
    private static AuctionManager instance;

    private final ConcurrentMap<String, Auction> auctions = new ConcurrentHashMap<>();
    private final ConcurrentSkipListSet<String> announcedEndedAuctionIds = new ConcurrentSkipListSet<>();
    private final UserManager userManager;
    private volatile AuctionDao auctionDao;

    private AuctionManager() {
        this.userManager = UserManager.getInstance();
    }

    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    // --- Message Handling Logic ---

    public void handleGetAuctionList(ClientHandler handler) {
        handler.sendMessage(JsonUtil.toJson(new AuctionListResponseMessage(getAuctionSummaries())));
    }

    public void handleGetAuctionDetails(GetAuctionDetailsRequestMessage message, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            Auction auction = requireAuction(message.getAuctionId());
            AuctionDetailsResponseMessage response = buildAuctionDetailsResponse(auction);
            handler.sendMessage(JsonUtil.toJson(response));
        } catch (AuctionAppException e) {
            handler.sendMessage(JsonUtil.toJson(new ErrorMessage(e.getMessage())));
        }
    }

    public void handleCreateItem(CreateItemRequestMessage message, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            Item item = createItemFromRequest(message);
            String sellerId = UserIdentityUtil.normalizeUserId(handler.getAuthenticatedId());
            Auction auction = createAuction(
                    sellerId,
                    item,
                    message.getStartingPrice(),
                    message.getMinimumBidIncrement(),
                    message.getStartTime(),
                    message.getEndTime()
            );
            persistAuction(auction);
            handler.sendMessage(JsonUtil.toJson(new CreateItemResultMessage(
                    auction.getId(),
                    auction.getItem().getTitle(),
                    "Auction created successfully."
            )));
        } catch (DatabaseException e) {
            handler.sendMessage(JsonUtil.toJson(new ErrorMessage("Failed to save auction to database: " + e.getMessage())));
        } catch (AuctionAppException e) {
            handler.sendMessage(JsonUtil.toJson(new ErrorMessage(e.getMessage())));
        }
    }

    public void handleBidRequest(BidRequestMessage message, ClientHandler handler) {
        String auctionId = message.getAuctionId();
        try {
            handler.ensureAuthenticated();
            String bidderId = UserIdentityUtil.normalizeUserId(handler.getAuthenticatedId());
            submitBid(auctionId, bidderId, BigDecimal.valueOf(message.getPrice()));

            Auction updatedAuction = requireAuction(auctionId);
            sendBidAccepted(handler, updatedAuction);
            broadcastPriceUpdate(updatedAuction);
            broadcastEndedAuctions();
        } catch (AuctionAppException e) {
            sendBidRejected(handler, auctionId, e.getMessage());
        }
    }

    public void broadcastEndedAuctions() {
        for (Auction auction : collectNewlyEndedAuctions()) {
            ServerApp.broadcast(JsonUtil.toJson(new AuctionEndedMessage(
                    auction.getId(),
                    auction.getWinnerBidderId(),
                    auction.getCurrentPrice()
            )));
        }
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

        User seller = userManager.requireSeller(UserIdentityUtil.normalizeUserId(sellerId));
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

        boolean updated = auction.updateListingDetails(
                title,
                description,
                startingPrice,
                minimumBidIncrement,
                startTime,
                endTime
        );
        if (!updated) {
            throw new InvalidAuctionStateException("Only open auctions can be edited with valid data.");
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
        User actor = userManager.requireUserById(UserIdentityUtil.normalizeUserId(actorId));
        ensureAdminOrOwningSeller(actor, auction);

        if (auction.getStatus() == AuctionStatus.RUNNING || auction.getStatus() == AuctionStatus.PAID) {
            throw new InvalidAuctionStateException("Running or paid auctions cannot be deleted.");
        }

        auction.cancel();
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

    public BidTransaction submitBid(String auctionId, String bidderId, BigDecimal amount) {
        Auction auction = requireAuction(auctionId);
        String normalizedBidderId = UserIdentityUtil.normalizeUserId(bidderId);
        userManager.requireBidder(normalizedBidderId);
        validateBidAmount(amount);
        ensureBidderIsNotSeller(auction, normalizedBidderId);

        refreshAuctionStatuses();
        ensureAuctionIsRunning(auction);
        ensureBidAmountMeetsMinimum(auction, amount);

        LocalDateTime now = LocalDateTime.now();
        BidTransaction bid = new BidTransaction(
                UUID.randomUUID().toString(),
                amount,
                now,
                normalizedBidderId,
                auctionId,
                false
        );

        if (!auction.placeBid(bid, now)) {
            throw new InvalidBidException("Bid was rejected by auction rules.");
        }
        persistAuctionState(auction);
        return bid;
    }

    public Auction finishAuction(String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.finish();
        persistAuctionState(auction);
        return auction;
    }

    public Auction markAuctionPaid(String auctionId) {
        Auction auction = requireAuction(auctionId);
        if (!auction.markPaid()) {
            throw new InvalidAuctionStateException("Only finished auctions with a winner can be marked as paid.");
        }
        persistAuctionState(auction);
        return auction;
    }

    public Auction cancelAuction(String actorId, String auctionId) {
        Auction auction = requireAuction(auctionId);
        User actor = userManager.requireUserById(UserIdentityUtil.normalizeUserId(actorId));
        ensureAdminOrOwningSeller(actor, auction);

        if (!auction.cancel()) {
            throw new InvalidAuctionStateException("Paid auctions cannot be canceled.");
        }
        persistAuctionState(auction);
        return auction;
    }

    public void refreshAuctionStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctions.values()) {
            AuctionStatus previousStatus = auction.getStatus();
            LocalDateTime previousEndTime = auction.getEndTime();
            auction.refreshStatus(now);
            if (previousStatus != auction.getStatus()
                    || !Objects.equals(previousEndTime, auction.getEndTime())) {
                persistAuctionState(auction);
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

    public List<Auction> collectNewlyEndedAuctions() {
        refreshAuctionStatuses();
        List<Auction> endedAuctions = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            if (auction.getStatus() == AuctionStatus.FINISHED && announcedEndedAuctionIds.add(auction.getId())) {
                endedAuctions.add(auction);
            }
        }
        return endedAuctions;
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
        User seller = userManager.requireSeller(UserIdentityUtil.normalizeUserId(sellerId));
        if (!Objects.equals(seller.getId(), auction.getSellerId())) {
            throw new AuthorizationException("Only the owning seller can modify this auction.");
        }
    }

    private void ensureAdminOrOwningSeller(User actor, Auction auction) {
        boolean isAdmin = actor.hasRole(UserRole.ADMIN);
        boolean isOwningSeller = actor.hasRole(UserRole.SELLER)
                && Objects.equals(actor.getId(), auction.getSellerId());
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

    private void ensureAuctionIsRunning(Auction auction) {
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new InvalidAuctionStateException("Auction is not open for bidding.");
        }
    }

    private void ensureBidAmountMeetsMinimum(Auction auction, BigDecimal amount) {
        BigDecimal minimumNextBid = auction.getMinimumNextBid();
        if (minimumNextBid != null && amount.compareTo(minimumNextBid) < 0) {
            throw new InvalidBidException("Bid must be at least " + minimumNextBid + ".");
        }
    }

    private void sendBidAccepted(ClientHandler handler, Auction auction) {
        synchronized (auction) {
            handler.sendMessage(JsonUtil.toJson(new BidResultMessage(
                    MessageType.BID_ACCEPTED,
                    auction.getId(),
                    auction.getCurrentPrice(),
                    auction.getLeadingBidderId(),
                    "Bid accepted."
            )));
        }
    }

    private void sendBidRejected(ClientHandler handler, String auctionId, String message) {
        handler.sendMessage(JsonUtil.toJson(new BidResultMessage(
                MessageType.BID_REJECTED,
                auctionId,
                null,
                null,
                message
        )));
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
}
