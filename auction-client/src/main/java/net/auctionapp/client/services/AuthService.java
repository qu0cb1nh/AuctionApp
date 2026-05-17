package net.auctionapp.client.services;

import net.auctionapp.client.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.users.UserRole;

public final class AuthService {
    private static final AuthService INSTANCE = new AuthService();

    private String currentUserId;
    private String currentUsername = "Guest";
    private UserRole currentUserRole = UserRole.USER;

    public String getCurrentUsername() {
        return currentUsername;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public UserRole getCurrentUserRole() {
        return currentUserRole;
    }

    public void setCurrentUser(String userId, String username, UserRole role) {
        this.currentUserId = userId;
        this.currentUsername = (username == null || username.isBlank()) ? "Guest" : username;
        this.currentUserRole = role == null ? UserRole.USER : role;
    }

    private AuthService() {
    }

    public static AuthService getInstance() {
        return INSTANCE;
    }

    public void login(String username, String password, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new LoginRequestMessage(username, password), callback);
    }

    public void register(String username, String password, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new RegisterRequestMessage(username, password), callback);
    }
}
