package net.auctionapp.server.managers;

import net.auctionapp.common.messages.types.AdminActionResultMessage;
import net.auctionapp.common.messages.types.AdminDeleteAuctionRequestMessage;
import net.auctionapp.common.messages.types.AdminForceCloseAuctionRequestMessage;
import net.auctionapp.common.messages.types.AdminGetUsersRequestMessage;
import net.auctionapp.common.messages.types.AdminGetUsersResponseMessage;
import net.auctionapp.common.messages.types.AdminResetAuctionRequestMessage;
import net.auctionapp.common.messages.types.AdminSetUserBanRequestMessage;
import net.auctionapp.common.messages.types.AdminUpdateAuctionRequestMessage;
import net.auctionapp.common.messages.types.AdminUserViewMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.models.users.User;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.exceptions.AuctionAppException;

import java.util.ArrayList;
import java.util.List;

public final class AdminManager {
    private static final AdminManager INSTANCE = new AdminManager();

    private final AuthManager authManager;
    private final AuctionManager auctionManager;
    private final SessionManager sessionManager;

    private AdminManager() {
        this.authManager = AuthManager.getInstance();
        this.auctionManager = AuctionManager.getInstance();
        this.sessionManager = SessionManager.getInstance();
    }

    public static AdminManager getInstance() {
        return INSTANCE;
    }

    public void handleGetUsers(AdminGetUsersRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            List<User> users = authManager.getAllUsers(actorId);
            List<AdminUserViewMessage> userViews = new ArrayList<>();
            for (User user : users) {
                userViews.add(new AdminUserViewMessage(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),
                        user.isBanned(),
                        sessionManager.isUserOnline(user.getId())
                ));
            }
            handler.sendResponse(new AdminGetUsersResponseMessage(userViews), request);
        } catch (RuntimeException e) {
            sendAdminError(handler, request, e);
        }
    }

    public void handleSetUserBan(AdminSetUserBanRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            User updatedUser = authManager.updateUserBanStatus(actorId, request.getUserId(), request.isBanned());
            String action = updatedUser.isBanned() ? "banned" : "unbanned";
            handler.sendResponse(new AdminActionResultMessage("User \"" + updatedUser.getUsername() + "\" was " + action + "."), request);
        } catch (RuntimeException e) {
            sendAdminError(handler, request, e);
        }
    }

    public void handleUpdateAuction(AdminUpdateAuctionRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            auctionManager.adminUpdateAuction(
                    actorId,
                    request.getAuctionId(),
                    request.getTitle(),
                    request.getDescription(),
                    request.getStartingPrice(),
                    request.getMinimumBidIncrement(),
                    request.getStartTime(),
                    request.getEndTime()
            );
            handler.sendResponse(new AdminActionResultMessage("Auction updated successfully."), request);
        } catch (RuntimeException e) {
            sendAdminError(handler, request, e);
        }
    }

    public void handleDeleteAuction(AdminDeleteAuctionRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            auctionManager.adminDeleteAuction(actorId, request.getAuctionId());
            handler.sendResponse(new AdminActionResultMessage("Auction deleted successfully."), request);
        } catch (RuntimeException e) {
            sendAdminError(handler, request, e);
        }
    }

    public void handleForceCloseAuction(AdminForceCloseAuctionRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            auctionManager.adminForceCloseAuction(actorId, request.getAuctionId());
            handler.sendResponse(new AdminActionResultMessage("Auction force-closed successfully."), request);
        } catch (RuntimeException e) {
            sendAdminError(handler, request, e);
        }
    }

    public void handleResetAuction(AdminResetAuctionRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            auctionManager.adminResetAuction(actorId, request.getAuctionId());
            handler.sendResponse(new AdminActionResultMessage("Auction reset successfully."), request);
        } catch (RuntimeException e) {
            sendAdminError(handler, request, e);
        }
    }

    private void sendAdminError(ClientHandler handler, net.auctionapp.common.messages.Message request, RuntimeException e) {
        String message = (e instanceof AuctionAppException) ? e.getMessage() : "Admin request failed.";
        handler.sendResponse(new ErrorMessage(message), request);
    }
}
