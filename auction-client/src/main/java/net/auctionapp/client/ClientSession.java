package net.auctionapp.client;

import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.StringUtil;

public final class ClientSession {
    private static final ClientSession INSTANCE = new ClientSession();

    private String userId;
    private String username = "Guest";
    private UserRole role = UserRole.USER;

    private ClientSession() {
    }

    public static ClientSession getInstance() {
        return INSTANCE;
    }

    public synchronized void login(String userId, String username, UserRole role) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        if (normalizedUserId.isEmpty()) {
            logout();
            return;
        }
        this.userId = normalizedUserId;
        this.username = username == null || username.isBlank() ? normalizedUserId : username.trim();
        this.role = role == null ? UserRole.USER : role;
    }

    public synchronized void logout() {
        userId = null;
        username = "Guest";
        role = UserRole.USER;
    }

    public synchronized boolean isAuthenticated() {
        return userId != null;
    }

    public synchronized String getUserId() {
        return userId;
    }

    public synchronized String getUsername() {
        return username;
    }

    public synchronized UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return getRole() == UserRole.ADMIN;
    }

    public boolean canCreateAuction() {
        return isAuthenticated();
    }

    public boolean canManageAuction(String sellerId) {
        if (!isAuthenticated()) {
            return false;
        }
        if (isAdmin()) {
            return true;
        }
        String normalizedSellerId = StringUtil.normalizeString(sellerId);
        if (normalizedSellerId.isEmpty()) {
            return false;
        }
        return normalizedSellerId.equals(StringUtil.normalizeString(getUserId()));
    }
}
