package net.auctionapp.common.messages.types;

import net.auctionapp.common.models.users.UserRole;

public class AdminUserViewMessage {
    private String userId;
    private String username;
    private UserRole role;
    private boolean banned;
    private boolean online;

    public AdminUserViewMessage() {
    }

    public AdminUserViewMessage(String userId, String username, UserRole role, boolean banned, boolean online) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.banned = banned;
        this.online = online;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isBanned() {
        return banned;
    }

    public boolean isOnline() {
        return online;
    }
}
