package net.auctionapp.common.dto;

import net.auctionapp.common.users.UserRole;

public class AdminUserDto {
    private String userId;
    private String username;
    private UserRole role;
    private boolean banned;
    private boolean online;

    public AdminUserDto() {
    }

    public AdminUserDto(String userId, String username, UserRole role, boolean banned, boolean online) {
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
