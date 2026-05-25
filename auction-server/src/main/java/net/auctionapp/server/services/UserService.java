package net.auctionapp.server.services;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.dto.AdminUserView;
import net.auctionapp.common.messages.admin.AdminActionResponseMessage;
import net.auctionapp.common.messages.admin.AdminGetUsersRequestMessage;
import net.auctionapp.common.messages.admin.AdminGetUsersResponseMessage;
import net.auctionapp.common.messages.admin.AdminSetUserBanRequestMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.AuthorizationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.managers.SessionManager;
import net.auctionapp.server.models.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class UserService {
    private static final UserService INSTANCE = new UserService();
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

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
            List<AdminUserView> userViews = new ArrayList<>();
            for (User user : users) {
                userViews.add(new AdminUserView(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),
                        user.isBanned(),
                        sessionManager.isUserOnline(user.getId())
                ));
            }
            handler.sendResponse(new AdminGetUsersResponseMessage(userViews), request);
        } catch (AuthenticationException | AuthorizationException | NotFoundException | ValidationException e) {
            sendUserError(handler, request, e.getMessage());
        } catch (DatabaseException e) {
            LOGGER.warn("Admin user listing failed: {}", e.getMessage(), e);
            sendUserError(handler, request, "Unable to load users.");
        } catch (RuntimeException e) {
            LOGGER.warn("Admin user listing failed unexpectedly: {}", e.getMessage(), e);
            sendUserError(handler, request, "User request failed.");
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
                    new AdminActionResponseMessage("User \"" + updatedUser.getUsername() + "\" was " + action + "."),
                    request
            );
        } catch (AuthenticationException | AuthorizationException | NotFoundException | ValidationException e) {
            sendUserError(handler, request, e.getMessage());
        } catch (DatabaseException e) {
            LOGGER.warn("Admin user update failed: {}", e.getMessage(), e);
            sendUserError(handler, request, "Unable to update user.");
        } catch (RuntimeException e) {
            LOGGER.warn("Admin user update failed unexpectedly: {}", e.getMessage(), e);
            sendUserError(handler, request, "User request failed.");
        }
    }

    private void sendUserError(ClientHandler handler, Message request, String message) {
        handler.sendResponse(new ErrorResponseMessage(message), request);
    }
}
