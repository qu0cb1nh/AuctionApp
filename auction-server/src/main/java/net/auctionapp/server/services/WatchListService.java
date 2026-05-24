package net.auctionapp.server.services;

import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetWatchListRequestMessage;
import net.auctionapp.common.messages.types.UpdateWatchListRequestMessage;
import net.auctionapp.common.messages.types.WatchListChangedMessage;
import net.auctionapp.common.messages.types.WatchListResponseMessage;
import net.auctionapp.common.messages.types.AuctionSummary;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.WatchListDao;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.exceptions.ValidationException;
import net.auctionapp.server.managers.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public final class WatchListService {
    private static final WatchListService INSTANCE = new WatchListService();
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchListService.class);
    private static final Duration ENDING_SOON_WINDOW = Duration.ofMinutes(5);

    private final SessionManager sessionManager;
    private final NotificationService notificationService;
    private volatile WatchListDao watchListDao;

    private WatchListService() {
        sessionManager = SessionManager.getInstance();
        notificationService = NotificationService.getInstance();
    }

    public static WatchListService getInstance() {
        return INSTANCE;
    }

    public void setWatchListDao(WatchListDao watchListDao) {
        this.watchListDao = watchListDao;
    }

    public void handleGetWatchList(GetWatchListRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = requireAuthenticatedUserId(handler);
            List<String> auctionIds = requireWatchListDao().findAuctionIdsByUserId(userId);
            handler.sendResponse(
                    new WatchListResponseMessage(AuctionService.getInstance().getAuctionSummaries(auctionIds)),
                    request
            );
        } catch (AuctionAppException e) {
            handler.sendResponse(new ErrorMessage(e.getMessage()), request);
        }
    }

    public void handleUpdateWatchList(UpdateWatchListRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = requireAuthenticatedUserId(handler);
            String auctionId = requireAuctionId(request == null ? null : request.getAuctionId());
            if (!AuctionService.getInstance().hasAuction(auctionId)) {
                throw new NotFoundException("Auction not found.");
            }

            boolean watched = request.isWatched();
            requireWatchListDao().setWatched(userId, auctionId, watched);
            handler.sendResponse(new WatchListChangedMessage(auctionId, watched), request);
            pushStateChanged(userId, auctionId, watched);
        } catch (AuctionAppException e) {
            handler.sendResponse(new ErrorMessage(e.getMessage()), request);
        }
    }

    public void sendEndingSoonReminders(AuctionSummary auction, LocalDateTime now) {
        if (!isEndingSoonReminderDue(auction, now)) {
            return;
        }
        List<String> recipientIds = requireWatchListDao().claimEndingSoonReminderRecipients(auction.getAuctionId());
        for (String recipientId : recipientIds) {
            try {
                notificationService.sendWatchListEndingSoonNotification(
                        recipientId,
                        auction.getAuctionId(),
                        auction.getTitle(),
                        auction.getEndTime()
                );
            } catch (AuctionAppException e) {
                LOGGER.warn(
                        "Failed to send watch list ending reminder for auction {} to user {}: {}",
                        auction.getAuctionId(),
                        recipientId,
                        e.getMessage()
                );
            }
        }
    }

    static boolean isEndingSoonReminderDue(AuctionSummary auction, LocalDateTime now) {
        if (auction == null || now == null || auction.getStatus() != AuctionStatus.RUNNING || auction.getEndTime() == null) {
            return false;
        }
        Duration remaining = Duration.between(now, auction.getEndTime());
        return !remaining.isNegative() && !remaining.isZero() && remaining.compareTo(ENDING_SOON_WINDOW) <= 0;
    }

    private void pushStateChanged(String userId, String auctionId, boolean watched) {
        for (ClientHandler client : sessionManager.getClientsByUserId(userId)) {
            client.sendMessage(new WatchListChangedMessage(auctionId, watched));
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
            throw new AuctionAppException("Watch list persistence is not configured.");
        }
        return watchListDao;
    }
}
