package net.auctionapp.client.services;

import net.auctionapp.client.ClientSession;
import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.auth.LoginRequestMessage;
import net.auctionapp.common.messages.auth.RegisterRequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final AuthService INSTANCE = new AuthService();

    public void logout() {
        LOGGER.info("Logging out current client session.");
        ClientSession.getInstance().logout();
    }

    private AuthService() {
    }

    public static AuthService getInstance() {
        return INSTANCE;
    }

    public void login(String username, String password, MessageListener<Message> callback) {
        LOGGER.info("Submitting login request for username '{}'.", username);
        NetworkService.getInstance().sendRequest(new LoginRequestMessage(username, password), callback);
    }

    public void register(String username, String password, MessageListener<Message> callback) {
        LOGGER.info("Submitting registration request for username '{}'.", username);
        NetworkService.getInstance().sendRequest(new RegisterRequestMessage(username, password), callback);
    }
}
