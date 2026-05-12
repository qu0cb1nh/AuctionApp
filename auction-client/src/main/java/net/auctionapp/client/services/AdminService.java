package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AdminDeleteAuctionRequestMessage;
import net.auctionapp.common.messages.types.AdminForceCloseAuctionRequestMessage;
import net.auctionapp.common.messages.types.AdminGetUsersRequestMessage;
import net.auctionapp.common.messages.types.AdminResetAuctionRequestMessage;
import net.auctionapp.common.messages.types.AdminSetUserBanRequestMessage;
import net.auctionapp.common.messages.types.AdminUpdateAuctionRequestMessage;

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

    public void updateAuction(AdminUpdateAuctionRequestMessage request, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(request, callback);
    }

    public void deleteAuction(String auctionId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new AdminDeleteAuctionRequestMessage(auctionId), callback);
    }

    public void forceCloseAuction(String auctionId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new AdminForceCloseAuctionRequestMessage(auctionId), callback);
    }

    public void resetAuction(String auctionId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new AdminResetAuctionRequestMessage(auctionId), callback);
    }
}
