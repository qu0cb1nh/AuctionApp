package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.*;
import net.auctionapp.common.models.auction.Auction;
import net.auctionapp.common.models.auction.BidTransaction;
import net.auctionapp.common.models.auction.AuctionStatus;
import net.auctionapp.common.models.items.*;
import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.ServerApp;
import net.auctionapp.server.exceptions.*;

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
            Auction auction = getAuctionById(message.getAuctionId())
                    .orElseThrow(() -> new AuctionAppException("Auction not found."));
            AuctionDetailsResponseMessage response = new AuctionDetailsResponseMessage(
                    auction.getId(),
                    auction.getSellerId(),
                    auction.getItem().getTitle(),
                    auction.getItem().getDescription(),
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
            handler.sendMessage(JsonUtil.toJson(response));
        } catch (AuctionAppException e) {
            handler.sendMessage(JsonUtil.toJson(new ErrorMessage(e.getMessage())));
        }
    }

    public void handleCreateItem(CreateItemRequestMessage message, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            Item item = createItemFromRequest(message);
            Auction auction = createAuction(
                    handler.getAuthenticatedId().toLowerCase(),
                    item,
                    message.getStartingPrice(),
                    message.getMinimumBidIncrement(),
                    message.getStartTime(),
                    message.getEndTime()
            );
            // After creating, send the new auction's details back to the creator
            handleGetAuctionDetails(new GetAuctionDetailsRequestMessage(auction.getId()), handler);
        } catch (AuctionAppException e) {
            handler.sendMessage(JsonUtil.toJson(new ErrorMessage(e.getMessage())));
        }
    }

    public void handleBidRequest(BidRequestMessage message, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            Auction auction = getAuctionById(message.getItemId())
                    .orElseThrow(() -> new AuctionAppException("Auction not found."));
            submitBid(
                    auction.getId(),
                    handler.getAuthenticatedId().toLowerCase(),
                    BigDecimal.valueOf(message.getPrice())
            );
            Auction updatedAuction = getAuctionById(auction.getId())
                    .orElseThrow(() -> new AuctionAppException("Auction not found."));
            handler.sendMessage(JsonUtil.toJson(new BidResultMessage(
                    MessageType.BID_ACCEPTED,
                    updatedAuction.getId(),
                    updatedAuction.getCurrentPrice(),
                    updatedAuction.getLeadingBidderId(),
                    "Bid accepted."
            )));
            ServerApp.broadcast(JsonUtil.toJson(new PriceUpdateMessage(
                    updatedAuction.getId(),
                    updatedAuction.getCurrentPrice().doubleValue(),
                    updatedAuction.getLeadingBidderId()
            )));
            broadcastEndedAuctions();
        } catch (AuctionAppException e) {
            handler.sendMessage(JsonUtil.toJson(new BidResultMessage(
                    MessageType.BID_REJECTED,
                    message.getItemId(),
                    null,
                    null,
                    e.getMessage()
            )));
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
        return new ArrayList<>(auctions.values());
    }

    public List<AuctionSummary> getAuctionSummaries() {
        refreshAuctionStatuses();
        List<AuctionSummary> result = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            result.add(new AuctionSummary(
                    auction.getId(),
                    auction.getItem().getTitle(),
                    auction.getCurrentPrice(),
                    auction.getMinimumNextBid(),
                    auction.getStatus(),
                    auction.getStartTime(),
                    auction.getEndTime()
            ));
        }
        return result;
    }

    public Optional<Auction> getAuctionById(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return Optional.empty();
        }
        refreshAuctionStatuses();
        return Optional.ofNullable(auctions.get(auctionId));
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

        userManager.requireUserById(sellerId);
        User seller = userManager.requireSeller(sellerId);

        Auction auction = new Auction(
                UUID.randomUUID().toString(),
                sellerId,
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
        User actor = userManager.requireUserById(actorId);

        boolean canDelete = actor.hasRole(UserRole.ADMIN) || Objects.equals(actor.getId(), auction.getSellerId());
        if (!canDelete) {
            throw new AuthorizationException("Only the seller or an admin can delete an auction.");
        }
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
        userManager.requireUserById(bidderId);
        User bidder = userManager.requireBidder(bidderId);
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Bid amount must be greater than zero.");
        }

        refreshAuctionStatuses();
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new InvalidAuctionStateException("Auction is not open for bidding.");
        }

        BigDecimal minimumNextBid = auction.getMinimumNextBid();
        if (minimumNextBid != null && amount.compareTo(minimumNextBid) < 0) {
            throw new InvalidBidException("Bid must be higher than the current minimum next bid.");
        }

        BidTransaction bid = new BidTransaction(
                UUID.randomUUID().toString(),
                amount,
                LocalDateTime.now(),
                bidderId,
                auctionId,
                false
        );

        if (!auction.placeBid(bid, LocalDateTime.now())) {
            throw new InvalidBidException("Bid was rejected.");
        }
        return bid;
    }

    public Auction finishAuction(String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.finish();
        return auction;
    }

    public Auction markAuctionPaid(String auctionId) {
        Auction auction = requireAuction(auctionId);
        if (!auction.markPaid()) {
            throw new InvalidAuctionStateException("Only finished auctions with a winner can be marked as paid.");
        }
        return auction;
    }

    public Auction cancelAuction(String actorId, String auctionId) {
        Auction auction = requireAuction(auctionId);
        User actor = userManager.requireUserById(actorId);

        boolean canCancel = actor.hasRole(UserRole.ADMIN) || Objects.equals(actor.getId(), auction.getSellerId());
        if (!canCancel) {
            throw new AuthorizationException("Only the seller or an admin can cancel an auction.");
        }
        if (!auction.cancel()) {
            throw new InvalidAuctionStateException("Paid auctions cannot be canceled.");
        }
        return auction;
    }

    public void refreshAuctionStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctions.values()) {
            auction.refreshStatus(now);
        }
    }

    public void clear() {
        auctions.clear();
        announcedEndedAuctionIds.clear();
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
        ItemFactory factory;
        switch (type) {
            case ART:
                factory = new ArtFactory();
                break;
            case ELECTRONICS:
                factory = new ElectronicsFactory();
                break;
            case VEHICLE:
                factory = new VehicleFactory();
                break;
            default:
                throw new AuctionAppException("Unsupported item type.");
        }
        return factory.createItem(message);
    }

    private Auction requireAuction(String auctionId) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new NotFoundException("Auction not found.");
        }
        return auction;
    }

    private void ensureSellerOwnsAuction(String sellerId, Auction auction) {
        userManager.requireSeller(sellerId);
        if (!Objects.equals(sellerId, auction.getSellerId())) {
            throw new AuthorizationException("Only the owning seller can modify this auction.");
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
