package net.auctionapp.client.services;

import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.admin.GetUserListRequestMessage;
import net.auctionapp.common.messages.admin.SetUserBanRequestMessage;

public final class AdminService {
    private static final AdminService INSTANCE = new AdminService();

    private AdminService() {
    }

    public static AdminService getInstance() {
        return INSTANCE;
    }

    public void requestUsers(MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetUserListRequestMessage(), callback);
    }

    public void updateUserBanStatus(String userId, boolean banned, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new SetUserBanRequestMessage(userId, banned), callback);
    }
}
