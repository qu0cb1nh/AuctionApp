package net.auctionapp.client;

import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.StringUtil;

public final class ClientSession {
    private static final ClientSession INSTANCE = new ClientSession();
    private static final String GUEST_USERNAME = "Guest";

    private String userId;
    private String username = GUEST_USERNAME;
    private UserRole role = UserRole.USER;
    private boolean authenticated;

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
        authenticated = true;
    }

    public synchronized void logout() {
        userId = null;
        username = GUEST_USERNAME;
        role = UserRole.USER;
        authenticated = false;
    }

    public synchronized boolean isAuthenticated() {
        return authenticated && userId != null && !userId.isBlank();
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
        String currentUserId = getUserId();
        String currentUsername = getUsername();
        return normalizedSellerId.equals(StringUtil.normalizeString(currentUserId))
                || normalizedSellerId.equals(StringUtil.normalizeString(currentUsername));
    }
}
