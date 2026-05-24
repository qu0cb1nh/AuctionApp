package net.auctionapp.server.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AdminActionResultMessage;
import net.auctionapp.common.messages.types.AdminGetUsersRequestMessage;
import net.auctionapp.common.messages.types.AdminGetUsersResponseMessage;
import net.auctionapp.common.messages.types.AdminSetUserBanRequestMessage;
import net.auctionapp.common.messages.types.AdminUserViewMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.managers.SessionManager;
import net.auctionapp.server.models.users.User;

import java.util.ArrayList;
import java.util.List;

public final class UserService {
    private static final UserService INSTANCE = new UserService();

    private final AuthService authService;
    private final AuctionService auctionService;
    private final SessionManager sessionManager;

    private UserService() {
        this.authService = AuthService.getInstance();
        this.auctionService = AuctionService.getInstance();
        this.sessionManager = SessionManager.getInstance();
    }

    public static UserService getInstance() {
        return INSTANCE;
    }

    public void handleGetUsers(AdminGetUsersRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            List<User> users = authService.getAllUsers(actorId);
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
            sendUserError(handler, request, e);
        }
    }

    public void handleSetUserBan(AdminSetUserBanRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            User updatedUser;
            if (request.isBanned()) {
                User targetUser = authService.validateUserBanStatusUpdate(actorId, request.getUserId(), true);
                auctionService.applyUserBanEffects(targetUser.getId());
                updatedUser = targetUser;
            } else {
                updatedUser = authService.updateUserBanStatus(actorId, request.getUserId(), false);
            }
            String action = updatedUser.isBanned() ? "banned" : "unbanned";
            handler.sendResponse(
                    new AdminActionResultMessage("User \"" + updatedUser.getUsername() + "\" was " + action + "."),
                    request
            );
        } catch (RuntimeException e) {
            sendUserError(handler, request, e);
        }
    }

    private void sendUserError(ClientHandler handler, Message request, RuntimeException e) {
        String message = (e instanceof AuctionAppException) ? e.getMessage() : "User request failed.";
        handler.sendResponse(new ErrorMessage(message), request);
    }
}
