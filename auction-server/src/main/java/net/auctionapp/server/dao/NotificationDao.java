package net.auctionapp.server.dao;

import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.common.notifications.Notification;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationDao {
    Notification createNotification(
            String userId,
            NotificationType type,
            String title,
            String body,
            String auctionId,
            LocalDateTime createdAt
    );

    List<Notification> findByUserId(String userId);

    boolean markAsRead(String userId, String notificationId);

    boolean clearById(String userId, String notificationId);
}
