package net.auctionapp.client.services;

import net.auctionapp.client.ClientSession;
import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.auth.LoginRequestMessage;
import net.auctionapp.common.messages.auth.RegisterRequestMessage;

public final class AuthService {
    private static final AuthService INSTANCE = new AuthService();

    public void logout() {
        ClientSession.getInstance().logout();
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
