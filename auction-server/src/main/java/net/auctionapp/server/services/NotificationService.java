package net.auctionapp.server.services;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.notification.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.notification.GetNotificationsRequestMessage;
import net.auctionapp.common.messages.notification.NotificationResponseMessage;
import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.common.messages.notification.NotificationsResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.notifications.Notification;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.NotificationDao;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.managers.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

public final class NotificationService {
    private static final NotificationService INSTANCE = new NotificationService();
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter END_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SessionManager sessionManager;
    private final AuthService authService;
    private volatile NotificationDao notificationDao;

    private NotificationService() {
        this.sessionManager = SessionManager.getInstance();
        this.authService = AuthService.getInstance();
    }

    public static NotificationService getInstance() {
        return INSTANCE;
    }

    public void setNotificationDao(NotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    public void handleGetNotifications(GetNotificationsRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = requireAuthenticatedUserId(handler);
            handler.sendResponse(new NotificationsResponseMessage(requireNotificationDao().findByUserId(userId)), request);
        } catch (AuthenticationException | NotFoundException | ValidationException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), request);
        } catch (DatabaseException e) {
            LOGGER.warn("Notification inbox request failed: {}", e.getMessage(), e);
            handler.sendResponse(new ErrorResponseMessage("Unable to load notifications."), request);
        }
    }

    public void handleClearNotifications(ClearNotificationsRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = requireAuthenticatedUserId(handler);
            String notificationId = requireNotificationId(request == null ? null : request.getNotificationId());
            boolean cleared = requireNotificationDao().clearById(userId, notificationId);
            if (!cleared) {
                throw new NotFoundException("Notification not found.");
            }
            sendCurrentInbox(userId, request, handler);
        } catch (AuthenticationException | NotFoundException | ValidationException e) {
            handler.sendResponse(new ErrorResponseMessage(e.getMessage()), request);
        } catch (DatabaseException e) {
            LOGGER.warn("Notification clear request failed: {}", e.getMessage(), e);
            handler.sendResponse(new ErrorResponseMessage("Unable to update notifications."), request);
        }
    }

    public void sendOutbidNotification(
            String displacedBidderId,
            String auctionId,
            String auctionTitle,
            BigDecimal newPrice,
            String newLeadingBidderId
    ) {
        String targetUserId = StringUtil.normalizeString(displacedBidderId);
        String normalizedLeaderId = StringUtil.normalizeString(newLeadingBidderId);
        if (targetUserId.isEmpty() || targetUserId.equals(normalizedLeaderId)) {
            return;
        }

        String safeTitle = auctionTitle == null || auctionTitle.isBlank() ? "an auction" : "\"" + auctionTitle + "\"";
        String priceText = newPrice == null ? "N/A" : "$" + newPrice.stripTrailingZeros().toPlainString();
        Notification notification = requireNotificationDao().createNotification(
                targetUserId,
                NotificationType.OUTBID,
                "You were outbid",
                "A higher bid was placed on " + safeTitle + ". Current price: " + priceText + ".",
                auctionId,
                LocalDateTime.now()
        );
        pushToOnlineClients(targetUserId, notification);
    }

    public void sendAuctionEndedNotifications(
            String auctionId,
            String auctionTitle,
            String sellerId,
            String winnerBidderId,
            BigDecimal finalPrice
    ) {
        String normalizedSellerId = StringUtil.normalizeString(sellerId);
        String normalizedWinnerId = StringUtil.normalizeString(winnerBidderId);
        String safeTitle = auctionTitle == null || auctionTitle.isBlank() ? "an auction" : "\"" + auctionTitle + "\"";
        String priceText = finalPrice == null ? "N/A" : "$" + finalPrice.stripTrailingZeros().toPlainString();

        if (!normalizedWinnerId.isEmpty()) {
            Notification winnerNotification = requireNotificationDao().createNotification(
                    normalizedWinnerId,
                    NotificationType.AUCTION_WON,
                    "You won an auction",
                    "You won " + safeTitle + ". Final price: " + priceText + ".",
                    auctionId,
                    LocalDateTime.now()
            );
            pushToOnlineClients(normalizedWinnerId, winnerNotification);
        }

        if (normalizedSellerId.isEmpty()) {
            return;
        }

        String sellerBody = normalizedWinnerId.isEmpty()
                ? "Your auction " + safeTitle + " ended with no bids."
                : "Your auction " + safeTitle + " ended. Winner: " + displayUsername(normalizedWinnerId)
                + ". Final price: " + priceText + ".";
        Notification sellerNotification = requireNotificationDao().createNotification(
                normalizedSellerId,
                NotificationType.AUCTION_SELLER_RESULT,
                "Your auction ended",
                sellerBody,
                auctionId,
                LocalDateTime.now()
        );
        pushToOnlineClients(normalizedSellerId, sellerNotification);
    }

    public void sendBidRemovalNotifications(
            String auctionId,
            String auctionTitle,
            String sellerId,
            String leadingBidderId
    ) {
        String safeTitle = auctionTitle == null || auctionTitle.isBlank() ? "an auction" : "\"" + auctionTitle + "\"";
        String normalizedLeaderId = StringUtil.normalizeString(leadingBidderId);
        if (!normalizedLeaderId.isEmpty()) {
            Notification leaderNotification = requireNotificationDao().createNotification(
                    normalizedLeaderId,
                    NotificationType.OUTBID,
                    "You are now the leading bidder",
                    "A higher bid was removed from " + safeTitle + ", so you are now the leading bidder.",
                    auctionId,
                    LocalDateTime.now()
            );
            pushToOnlineClients(normalizedLeaderId, leaderNotification);
        }

        String normalizedSellerId = StringUtil.normalizeString(sellerId);
        if (normalizedSellerId.isEmpty()) {
            return;
        }
        Notification sellerNotification = requireNotificationDao().createNotification(
                normalizedSellerId,
                NotificationType.AUCTION_SELLER_RESULT,
                "The leading bid changed",
                "The leading bid on " + safeTitle + " changed because a bid was removed.",
                auctionId,
                LocalDateTime.now()
        );
        pushToOnlineClients(normalizedSellerId, sellerNotification);
    }

    public void sendWatchListEndingSoonNotification(
            String userId,
            String auctionId,
            String auctionTitle,
            LocalDateTime endTime
    ) {
        String targetUserId = StringUtil.normalizeString(userId);
        if (targetUserId.isEmpty()) {
            return;
        }
        String safeTitle = auctionTitle == null || auctionTitle.isBlank() ? "an auction" : "\"" + auctionTitle + "\"";
        String endTimeText = endTime == null ? "soon" : endTime.format(END_TIME_FORMATTER);
        Notification notification = requireNotificationDao().createNotification(
                targetUserId,
                NotificationType.WATCH_LIST_ENDING_SOON,
                "Saved auction ending soon",
                "Your saved auction " + safeTitle + " ends at " + endTimeText + ".",
                auctionId,
                LocalDateTime.now()
        );
        pushToOnlineClients(targetUserId, notification);
    }

    private void sendCurrentInbox(String userId, net.auctionapp.common.messages.Message request, ClientHandler handler) {
        List<Notification> notifications = requireNotificationDao().findByUserId(userId);
        handler.sendResponse(new NotificationsResponseMessage(notifications), request);
    }

    private void pushToOnlineClients(String userId, Notification notification) {
        Set<ClientHandler> clients = sessionManager.getClientsByUserId(userId);
        if (clients.isEmpty()) {
            return;
        }
        String normalizedTargetUserId = StringUtil.normalizeString(userId);
        NotificationResponseMessage pushMessage = new NotificationResponseMessage(notification);
        for (ClientHandler clientHandler : clients) {
            String authenticatedUserId = StringUtil.normalizeString(clientHandler.getAuthenticatedId());
            if (!normalizedTargetUserId.equals(authenticatedUserId)) {
                continue;
            }
            if (sessionManager.getSession(clientHandler)
                    .map(SessionManager.SessionInfo::userId)
                    .map(StringUtil::normalizeString)
                    .filter(sessionUserId -> sessionUserId.equals(normalizedTargetUserId))
                    .isEmpty()) {
                continue;
            }
            clientHandler.sendMessage(pushMessage);
        }
    }

    private String requireAuthenticatedUserId(ClientHandler handler) {
        String userId = StringUtil.normalizeString(handler.getAuthenticatedId());
        if (userId.isEmpty()) {
            throw new ValidationException("Authenticated user is required.");
        }
        return userId;
    }

    private String requireNotificationId(String notificationId) {
        String normalized = notificationId == null ? "" : notificationId.trim();
        if (normalized.isEmpty()) {
            throw new ValidationException("Notification id is required.");
        }
        return normalized;
    }

    private NotificationDao requireNotificationDao() {
        if (notificationDao == null) {
            throw new DatabaseException("Notification persistence is not configured.");
        }
        return notificationDao;
    }

    private String displayUsername(String userId) {
        try {
            return authService.requireUserById(userId).getUsername();
        } catch (NotFoundException | DatabaseException e) {
            return userId;
        }
    }
}
