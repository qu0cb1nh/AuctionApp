package net.auctionapp.server.managers;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.watchlist.GetWatchListRequestMessage;
import net.auctionapp.common.messages.watchlist.UpdateWatchListRequestMessage;
import net.auctionapp.common.messages.watchlist.WatchListChangedResponseMessage;
import net.auctionapp.common.messages.watchlist.WatchListResponseMessage;
import net.auctionapp.common.dto.AuctionSummaryDto;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.WatchListDao;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.messages.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public final class WatchListManager {
    private static final WatchListManager INSTANCE = new WatchListManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchListManager.class);
    private static final Duration ENDING_SOON_WINDOW = Duration.ofMinutes(5);

    private final SessionManager sessionManager;
    private final NotificationManager notificationManager;
    private volatile WatchListDao watchListDao;

    private WatchListManager() {
        sessionManager = SessionManager.getInstance();
        notificationManager = NotificationManager.getInstance();
    }

    public static WatchListManager getInstance() {
        return INSTANCE;
    }

    public void setWatchListDao(WatchListDao watchListDao) {
        this.watchListDao = watchListDao;
    }

    public void registerCommands(MessageRouter messageRouter) {
        messageRouter.register(MessageType.GET_WATCH_LIST_REQUEST, GetWatchListRequestMessage.class,
                this::handleGetWatchList);
        messageRouter.register(MessageType.UPDATE_WATCH_LIST_REQUEST, UpdateWatchListRequestMessage.class,
                this::handleUpdateWatchList);
    }

    public void handleGetWatchList(GetWatchListRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = requireAuthenticatedUserId(handler);
            List<String> auctionIds = requireWatchListDao().findAuctionIdsByUserId(userId);
            handler.sendResponse(
                    new WatchListResponseMessage(AuctionManager.getInstance().getAuctionSummaries(auctionIds)),
                    request
            );
        } catch (AuthenticationException | NotFoundException | ValidationException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), request);
        } catch (DatabaseException e) {
            LOGGER.warn("Watch list request failed: {}", e.getMessage(), e);
            handler.sendResponse(new ErrorResponseMessage("Unable to load watch list."), request);
        }
    }

    public void handleUpdateWatchList(UpdateWatchListRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = requireAuthenticatedUserId(handler);
            String auctionId = requireAuctionId(request.getAuctionId());
            if (!AuctionManager.getInstance().hasAuction(auctionId)) {
                throw new NotFoundException("Auction not found.");
            }

            boolean watched = request.isWatched();
            requireWatchListDao().setWatched(userId, auctionId, watched);
            handler.sendResponse(new WatchListChangedResponseMessage(auctionId, watched), request);
            pushStateChanged(userId, auctionId, watched);
        } catch (AuthenticationException | NotFoundException | ValidationException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), request);
        } catch (DatabaseException e) {
            LOGGER.warn("Watch list update failed: {}", e.getMessage(), e);
            handler.sendResponse(new ErrorResponseMessage("Unable to update watch list."), request);
        }
    }

    public void sendEndingSoonReminders(AuctionSummaryDto auction, LocalDateTime now) {
        if (!isEndingSoonReminderDue(auction, now)) {
            return;
        }
        List<String> recipientIds = requireWatchListDao().claimEndingSoonReminderRecipients(auction.getAuctionId());
        for (String recipientId : recipientIds) {
            try {
                notificationManager.sendWatchListEndingSoonNotification(
                        recipientId,
                        auction.getAuctionId(),
                        auction.getTitle(),
                        auction.getEndTime()
                );
            } catch (DatabaseException e) {
                LOGGER.warn(
                        "Failed to send watch list ending reminder for auction {} to user {}: {}",
                        auction.getAuctionId(),
                        recipientId,
                        e.getMessage()
                );
            }
        }
    }

    static boolean isEndingSoonReminderDue(AuctionSummaryDto auction, LocalDateTime now) {
        if (auction == null || now == null || auction.getStatus() != AuctionStatus.RUNNING || auction.getEndTime() == null) {
            return false;
        }
        Duration remaining = Duration.between(now, auction.getEndTime());
        return !remaining.isNegative() && !remaining.isZero() && remaining.compareTo(ENDING_SOON_WINDOW) <= 0;
    }

    private void pushStateChanged(String userId, String auctionId, boolean watched) {
        for (ClientHandler client : sessionManager.getClientsByUserId(userId)) {
            client.sendMessage(new WatchListChangedResponseMessage(auctionId, watched));
        }
    }

    private String requireAuthenticatedUserId(ClientHandler handler) {
        String userId = StringUtil.normalizeString(handler.getAuthenticatedId());
        if (userId.isEmpty()) {
            throw new ValidationException("Authenticated user is required.");
        }
        return userId;
    }

    private String requireAuctionId(String auctionId) {
        String value = auctionId == null ? "" : auctionId.trim();
        if (value.isEmpty()) {
            throw new ValidationException("Auction id is required.");
        }
        return value;
    }

    private WatchListDao requireWatchListDao() {
        if (watchListDao == null) {
            throw new DatabaseException("Watch list persistence is not configured.");
        }
        return watchListDao;
    }
}
