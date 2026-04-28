package net.auctionapp.common.notifications;

import java.time.LocalDateTime;

public class NotificationView {
    private String id;
    private String userId;
    private NotificationType type;
    private String title;
    private String body;
    private String auctionId;
    private LocalDateTime createdAt;
    private boolean read;

    public NotificationView() {
    }

    public NotificationView(
            String id,
            String userId,
            NotificationType type,
            String title,
            String body,
            String auctionId,
            LocalDateTime createdAt,
            boolean read
    ) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.auctionId = auctionId;
        this.createdAt = createdAt;
        this.read = read;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }
}
