package net.auctionapp.client.services;

import net.auctionapp.client.ClientSession;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;

import java.util.concurrent.CompletableFuture;

public final class AuthService {
    private static final AuthService INSTANCE = new AuthService();

    private String cachedUsername;
    private String cachedPassword;

    public void cacheLoginCredentials(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        cachedUsername = username.trim();
        cachedPassword = password;
    }

    public boolean hasCachedCredentials() {
        return cachedUsername != null && !cachedUsername.isBlank()
                && cachedPassword != null && !cachedPassword.isBlank();
    }

    public void clearCachedCredentials() {
        cachedUsername = null;
        cachedPassword = null;
    }

    public void clearSessionAndCredentials() {
        ClientSession.getInstance().logout();
        clearCachedCredentials();
    }

    private AuthService() {
    }

    public static AuthService getInstance() {
        return INSTANCE;
    }

    public void login(String username, String password, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new LoginRequestMessage(username, password), callback);
    }

    public CompletableFuture<Message> autoLogin() {
        if (!hasCachedCredentials()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No cached credentials available."));
        }
        return NetworkService.getInstance().sendRequest(new LoginRequestMessage(cachedUsername, cachedPassword));
    }

    public void register(String username, String password, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new RegisterRequestMessage(username, password), callback);
    }
}
