package net.auctionapp.server.managers;

import net.auctionapp.common.messages.types.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetNotificationsRequestMessage;
import net.auctionapp.common.messages.types.MarkNotificationReadRequestMessage;
import net.auctionapp.common.messages.types.NotificationMessage;
import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.common.messages.types.NotificationsResponseMessage;
import net.auctionapp.common.notifications.Notification;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.NotificationDao;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.exceptions.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class NotificationManager {
    private static final NotificationManager INSTANCE = new NotificationManager();

    private final SessionManager sessionManager;
    private volatile NotificationDao notificationDao;

    private NotificationManager() {
        this.sessionManager = SessionManager.getInstance();
    }

    public static NotificationManager getInstance() {
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
        } catch (AuctionAppException e) {
            handler.sendResponse(new ErrorMessage(e.getMessage()), request);
        }
    }

    public void handleMarkNotificationRead(MarkNotificationReadRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String userId = requireAuthenticatedUserId(handler);
            String notificationId = requireNotificationId(request == null ? null : request.getNotificationId());
            boolean marked = requireNotificationDao().markAsRead(userId, notificationId);
            if (!marked) {
                throw new NotFoundException("Notification not found.");
            }
            sendCurrentInbox(userId, request, handler);
        } catch (AuctionAppException e) {
            handler.sendResponse(new ErrorMessage(e.getMessage()), request);
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
        } catch (AuctionAppException e) {
            handler.sendResponse(new ErrorMessage(e.getMessage()), request);
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
        NotificationMessage pushMessage = new NotificationMessage(normalizedTargetUserId, notification);
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
            throw new AuctionAppException("Notification persistence is not configured.");
        }
        return notificationDao;
    }
}
