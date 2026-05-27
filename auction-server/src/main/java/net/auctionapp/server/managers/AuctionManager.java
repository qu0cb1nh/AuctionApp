package net.auctionapp.server.managers;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.dto.AuctionSummaryDto;
import net.auctionapp.common.messages.auction.AuctionActionResponseMessage;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.auction.AuctionListResponseMessage;
import net.auctionapp.common.messages.auction.BidRequestMessage;
import net.auctionapp.common.messages.auction.BidResponseMessage;
import net.auctionapp.common.messages.auction.CancelAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CloseAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.auction.GetMyActivityRequestMessage;
import net.auctionapp.common.messages.auction.GetMyListingsRequestMessage;
import net.auctionapp.common.messages.auction.MyActivityResponseMessage;
import net.auctionapp.common.messages.auction.MyListingsResponseMessage;
import net.auctionapp.common.messages.auction.ObserveAuctionRequestMessage;
import net.auctionapp.common.messages.auction.UpdateAuctionRequestMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.server.managers.auction.*;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.AuctionDao;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.messages.MessageRouter;
import net.auctionapp.server.services.CloudinaryImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central in-memory manager for auction sessions.
 */
public final class AuctionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionManager.class);
    private static AuctionManager instance;

    private final NotificationManager notificationManager;
    private final AuctionQuery auctionQuery;
    private final AuctionBroadcaster auctionBroadcaster;
    private final AuctionPersistence auctionPersistence;
    private final AuctionCreation auctionCreation;
    private final AuctionBid auctionBid;
    private final AuctionLifecycle auctionLifecycle;
    private final AuctionBanEffect auctionBanEffect;

    private AuctionManager() {
        ConcurrentMap<String, Auction> auctions = new ConcurrentHashMap<>();
        AuctionSafeUpdateExecutor auctionMutations = new AuctionSafeUpdateExecutor();
        Clock clock = Clock.systemDefaultZone();
        AuthManager authManager = AuthManager.getInstance();
        this.notificationManager = NotificationManager.getInstance();
        WalletManager walletManager = WalletManager.getInstance();
        this.auctionQuery = new AuctionQuery(auctions, authManager);
        this.auctionBroadcaster = new AuctionBroadcaster(auctionQuery, notificationManager);
        this.auctionPersistence = new AuctionPersistence(auctions, walletManager);
        this.auctionLifecycle = new AuctionLifecycle(
                auctions,
                auctionMutations,
                authManager,
                auctionQuery,
                auctionPersistence,
                auctionBroadcaster,
                WatchListManager.getInstance(),
                clock
        );
        this.auctionCreation = new AuctionCreation(
                auctions,
                auctionMutations,
                authManager,
                CloudinaryImageService.getInstance(),
                auctionPersistence,
                clock
        );
        this.auctionBid = new AuctionBid(
                auctionMutations,
                authManager,
                walletManager,
                auctionQuery,
                auctionPersistence,
                clock
        );
        this.auctionBanEffect = new AuctionBanEffect(
                auctions,
                auctionMutations,
                authManager,
                notificationManager,
                walletManager,
                auctionPersistence,
                auctionBroadcaster
        );
    }

    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    public void registerCommands(MessageRouter messageRouter) {
        messageRouter.register(MessageType.GET_AUCTION_LIST_REQUEST, GetAuctionListRequestMessage.class,
                this::handleGetAuctionList);
        messageRouter.register(MessageType.GET_AUCTION_DETAILS_REQUEST, GetAuctionDetailsRequestMessage.class,
                this::handleGetAuctionDetails);
        messageRouter.register(MessageType.GET_MY_ACTIVITY_REQUEST, GetMyActivityRequestMessage.class,
                this::handleGetMyActivity);
        messageRouter.register(MessageType.GET_MY_LISTINGS_REQUEST, GetMyListingsRequestMessage.class,
                this::handleGetMyListings);
        messageRouter.register(MessageType.OBSERVE_AUCTION_REQUEST, ObserveAuctionRequestMessage.class,
                this::handleObserveAuction);
        messageRouter.register(MessageType.CREATE_ITEM_REQUEST, CreateItemRequestMessage.class, this::handleCreateItem);
        messageRouter.register(MessageType.BID_REQUEST, BidRequestMessage.class, this::handleBidRequest);
        messageRouter.register(MessageType.UPDATE_AUCTION_REQUEST, UpdateAuctionRequestMessage.class,
                this::handleUpdateAuction);
        messageRouter.register(MessageType.CANCEL_AUCTION_REQUEST, CancelAuctionRequestMessage.class,
                this::handleCancelAuction);
        messageRouter.register(MessageType.CLOSE_AUCTION_REQUEST, CloseAuctionRequestMessage.class,
                this::handleCloseAuction);
    }

    // --- Message Handling Logic ---

    public void handleGetAuctionList(GetAuctionListRequestMessage request, ClientHandler handler) {
        handler.sendResponse(new AuctionListResponseMessage(auctionQuery.getAuctionSummaries()), request);
    }

    public void handleGetAuctionDetails(GetAuctionDetailsRequestMessage message, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            Auction auction = auctionQuery.requireAuction(message.getAuctionId());
            AuctionDetailsResponseMessage response = auctionQuery.buildAuctionDetailsResponse(auction);
            handler.sendResponse(response, message);
        } catch (RuntimeException e) {
            sendError(handler, message, e.getMessage());
        }
    }

    public void handleGetMyActivity(GetMyActivityRequestMessage request, ClientHandler handler) {
        try {
            String userId = requireAuthenticatedUserId(handler);
            handler.sendResponse(new MyActivityResponseMessage(auctionQuery.getActivityForUser(userId)), request);
        } catch (RuntimeException e) {
            sendError(handler, request, e.getMessage());
        }
    }

    public void handleGetMyListings(GetMyListingsRequestMessage request, ClientHandler handler) {
        try {
            String userId = requireAuthenticatedUserId(handler);
            handler.sendResponse(new MyListingsResponseMessage(auctionQuery.getListingsForUser(userId)), request);
        } catch (RuntimeException e) {
            sendError(handler, request, e.getMessage());
        }
    }

    public void handleObserveAuction(ObserveAuctionRequestMessage message, ClientHandler handler) {
        if (!message.isObserving()) {
            auctionBroadcaster.unsubscribe(message.getAuctionId(), handler);
            return;
        }
        try {
            handler.ensureAuthenticated();
            auctionBroadcaster.subscribe(message.getAuctionId(), handler);
        } catch (RuntimeException e) {
            sendError(handler, message, e.getMessage());
        }
    }

    public void removeSubscriber(ClientHandler handler) {
        auctionBroadcaster.removeSubscriber(handler);
    }

    public void handleCreateItem(CreateItemRequestMessage message, ClientHandler handler) {
        try {
            handler.sendResponse(
                    auctionCreation.createAuction(message, requireAuthenticatedUserId(handler)),
                    message
            );
        } catch (RuntimeException e) {
            sendError(handler, message, e.getMessage());
        }
    }

    public void handleBidRequest(BidRequestMessage message, ClientHandler handler) {
        String auctionId = message.getAuctionId();
        try {
            String bidderId = requireAuthenticatedUserId(handler);
            AuctionBid.BidResult result = auctionBid.submitBid(auctionId, bidderId, message.getPrice());
            sendBidAccepted(handler, message, result.auction());
            auctionBroadcaster.broadcastPriceUpdate(result.auction());
            trySendOutbidNotification(result.previousLeadingBidderId(), result.auction());
        } catch (RuntimeException e) {
            handler.sendResponse(new BidResponseMessage(
                    MessageType.BID_REJECTED,
                    auctionId,
                    null,
                    null,
                    e.getMessage()
            ), message);
        }
    }

    public void handleUpdateAuction(UpdateAuctionRequestMessage request, ClientHandler handler) {
        try {
            String actorId = requireAuthenticatedUserId(handler);
            Auction updatedAuction = auctionLifecycle.updateAuction(
                    actorId,
                    request.getAuctionId(),
                    request.getTitle(),
                    request.getDescription(),
                    request.getEndTime()
            );
            sendAuctionActionSuccess(handler, request, updatedAuction, "Auction updated successfully.");
        } catch (RuntimeException e) {
            sendError(handler, request, e.getMessage());
        }
    }

    public void handleCancelAuction(CancelAuctionRequestMessage request, ClientHandler handler) {
        try {
            String actorId = requireAuthenticatedUserId(handler);
            Auction updatedAuction = auctionLifecycle.cancelAuction(actorId, request.getAuctionId());
            sendAuctionActionSuccess(handler, request, updatedAuction, "Auction canceled successfully.");
        } catch (RuntimeException e) {
            sendError(handler, request, e.getMessage());
        }
    }

    public void handleCloseAuction(CloseAuctionRequestMessage request, ClientHandler handler) {
        try {
            String actorId = requireAuthenticatedUserId(handler);
            Auction updatedAuction = auctionLifecycle.closeAuction(actorId, request.getAuctionId());
            sendAuctionActionSuccess(handler, request, updatedAuction, "Auction closed successfully.");
        } catch (RuntimeException e) {
            sendError(handler, request, e.getMessage());
        }
    }

    public synchronized void startStatusScheduler() {
        auctionLifecycle.start();
    }

    public synchronized void stopStatusScheduler() {
        auctionLifecycle.stop();
    }

    public List<AuctionSummaryDto> getAuctionSummaries(Iterable<String> auctionIds) {
        return auctionQuery.getAuctionSummaries(auctionIds);
    }

    public boolean hasAuction(String auctionId) {
        return auctionQuery.hasAuction(auctionId);
    }

    private String requireAuthenticatedUserId(ClientHandler handler) {
        handler.ensureAuthenticated();
        return StringUtil.normalizeString(handler.getAuthenticatedId());
    }

    public void applyUserBanEffects(String bannedUserId) {
        auctionBanEffect.applyUserBanEffects(bannedUserId);
    }

    public synchronized void setAuctionDao(AuctionDao auctionDao) {
        auctionPersistence.setAuctionDao(auctionDao);
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

    private void sendAuctionActionSuccess(
            ClientHandler handler,
            Message request,
            Auction auction,
            String message
    ) {
        handler.sendResponse(new AuctionActionResponseMessage(message), request);
        auctionBroadcaster.broadcastAuctionUpdated(auction);
    }

    private void sendError(ClientHandler handler, Message request, String message) {
        handler.sendResponse(new ErrorResponseMessage(message), request);
    }

    private void trySendOutbidNotification(String previousLeadingBidderId, Auction updatedAuction) {
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
        } catch (DatabaseException e) {
            LOGGER.warn("Failed to send outbid notification for auction {}: {}", updatedAuction.getId(), e.getMessage());
        }
    }
}
