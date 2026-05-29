package net.auctionapp.server.managers;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.MessageType;
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
import net.auctionapp.server.messages.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public final class NotificationManager {
    private static final NotificationManager INSTANCE = new NotificationManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationManager.class);
    private static final DateTimeFormatter END_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SessionManager sessionManager;
    private final AuthManager authManager;
    private volatile NotificationDao notificationDao;

    private NotificationManager() {
        this.sessionManager = SessionManager.getInstance();
        this.authManager = AuthManager.getInstance();
    }

    public static NotificationManager getInstance() {
        return INSTANCE;
    }

    public void setNotificationDao(NotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    public void registerCommands(MessageRouter messageRouter) {
        messageRouter.register(MessageType.GET_NOTIFICATIONS_REQUEST, GetNotificationsRequestMessage.class,
                this::handleGetNotifications);
        messageRouter.register(MessageType.CLEAR_NOTIFICATIONS_REQUEST, ClearNotificationsRequestMessage.class,
                this::handleClearNotifications);
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
            if (request.isClearAll()) {
                requireNotificationDao().clearByUserId(userId);
            } else {
                String notificationId = requireNotificationId(request.getNotificationId());
                boolean cleared = requireNotificationDao().clearById(userId, notificationId);
                if (!cleared) {
                    throw new NotFoundException("Notification not found.");
                }
            }
            handler.sendResponse(
                    new NotificationsResponseMessage(requireNotificationDao().findByUserId(userId)),
                    request
            );
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

        String safeTitle = formatAuctionTitle(auctionTitle);
        String priceText = formatPrice(newPrice);
        createAndPush(
                targetUserId,
                NotificationType.OUTBID,
                "You were outbid",
                "A higher bid was placed on " + safeTitle + ". Current price: " + priceText + ".",
                auctionId
        );
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
        String safeTitle = formatAuctionTitle(auctionTitle);
        String priceText = formatPrice(finalPrice);

        if (!normalizedWinnerId.isEmpty()) {
            createAndPush(
                    normalizedWinnerId,
                    NotificationType.AUCTION_WON,
                    "You won an auction",
                    "You won " + safeTitle + ". Final price: " + priceText + ".",
                    auctionId
            );
        }

        if (normalizedSellerId.isEmpty()) {
            return;
        }

        String sellerBody = normalizedWinnerId.isEmpty()
                ? "Your auction " + safeTitle + " ended with no bids."
                : "Your auction " + safeTitle + " ended. Winner: " + displayUsername(normalizedWinnerId)
                + ". Final price: " + priceText + ".";
        createAndPush(
                normalizedSellerId,
                NotificationType.AUCTION_SELLER_RESULT,
                "Your auction ended",
                sellerBody,
                auctionId
        );
    }

    public void sendBidRemovalNotifications(
            String auctionId,
            String auctionTitle,
            String sellerId,
            String leadingBidderId
    ) {
        String safeTitle = formatAuctionTitle(auctionTitle);
        String normalizedLeaderId = StringUtil.normalizeString(leadingBidderId);
        if (!normalizedLeaderId.isEmpty()) {
            createAndPush(
                    normalizedLeaderId,
                    NotificationType.OUTBID,
                    "You are now the leading bidder",
                    "A higher bid was removed from " + safeTitle + ", so you are now the leading bidder.",
                    auctionId
            );
        }

        String normalizedSellerId = StringUtil.normalizeString(sellerId);
        if (normalizedSellerId.isEmpty()) {
            return;
        }
        createAndPush(
                normalizedSellerId,
                NotificationType.AUCTION_SELLER_RESULT,
                "The leading bid changed",
                "The leading bid on " + safeTitle + " changed because a bid was removed.",
                auctionId
        );
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
        String safeTitle = formatAuctionTitle(auctionTitle);
        String endTimeText = endTime == null ? "soon" : endTime.format(END_TIME_FORMATTER);
        createAndPush(
                targetUserId,
                NotificationType.WATCH_LIST_ENDING_SOON,
                "Saved auction ending soon",
                "Your saved auction " + safeTitle + " ends at " + endTimeText + ".",
                auctionId
        );
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

    private void createAndPush(
            String userId,
            NotificationType type,
            String title,
            String body,
            String auctionId
    ) {
        Notification notification = requireNotificationDao().createNotification(
                userId,
                type,
                title,
                body,
                auctionId,
                LocalDateTime.now()
        );
        pushToOnlineClients(userId, notification);
    }

    private String formatAuctionTitle(String auctionTitle) {
        return auctionTitle == null || auctionTitle.isBlank() ? "an auction" : "\"" + auctionTitle + "\"";
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : "$" + price.stripTrailingZeros().toPlainString();
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
            return authManager.requireUserById(userId).getUsername();
        } catch (NotFoundException | DatabaseException e) {
            return userId;
        }
    }
}
