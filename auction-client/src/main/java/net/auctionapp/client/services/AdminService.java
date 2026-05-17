package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AdminGetUsersRequestMessage;
import net.auctionapp.common.messages.types.AdminSetUserBanRequestMessage;

public final class AdminService {
    private static final AdminService INSTANCE = new AdminService();

    private AdminService() {
    }

    public static AdminService getInstance() {
        return INSTANCE;
    }

    public void requestUsers(MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new AdminGetUsersRequestMessage(), callback);
    }

    public void updateUserBanStatus(String userId, boolean banned, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new AdminSetUserBanRequestMessage(userId, banned), callback);
    }
}
