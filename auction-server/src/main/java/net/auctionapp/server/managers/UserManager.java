package net.auctionapp.server.managers;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.admin.SetUserBanResponseMessage;
import net.auctionapp.common.messages.admin.GetUserListRequestMessage;
import net.auctionapp.common.messages.admin.GetUserListResponseMessage;
import net.auctionapp.common.dto.AdminUserDto;
import net.auctionapp.common.messages.admin.SetUserBanRequestMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.AuthorizationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.messages.MessageRouter;
import net.auctionapp.server.models.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class UserManager {
    private static final UserManager INSTANCE = new UserManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(UserManager.class);

    private final AuthManager authManager;
    private final AuctionManager auctionManager;
    private final SessionManager sessionManager;

    private UserManager() {
        this.authManager = AuthManager.getInstance();
        this.auctionManager = AuctionManager.getInstance();
        this.sessionManager = SessionManager.getInstance();
    }

    public static UserManager getInstance() {
        return INSTANCE;
    }

    public void registerCommands(MessageRouter messageRouter) {
        messageRouter.register(MessageType.GET_USER_LIST_REQUEST, GetUserListRequestMessage.class,
                this::handleGetUsers);
        messageRouter.register(MessageType.SET_USER_BAN_REQUEST, SetUserBanRequestMessage.class,
                this::handleSetUserBan);
    }

    public void handleGetUsers(GetUserListRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            List<User> users = authManager.getAllUsers(actorId);
            List<AdminUserDto> userViews = new ArrayList<>();
            for (User user : users) {
                userViews.add(new AdminUserDto(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),
                        user.isBanned(),
                        sessionManager.isUserOnline(user.getId())
                ));
            }
            handler.sendResponse(new GetUserListResponseMessage(userViews), request);
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

    public void handleSetUserBan(SetUserBanRequestMessage request, ClientHandler handler) {
        try {
            handler.ensureAuthenticated();
            String actorId = StringUtil.normalizeString(handler.getAuthenticatedId());
            User updatedUser;
            if (request.isBanned()) {
                User targetUser = authManager.validateUserBan(actorId, request.getUserId());
                auctionManager.applyUserBanEffects(targetUser.getId());
                updatedUser = targetUser;
            } else {
                updatedUser = authManager.unbanUser(actorId, request.getUserId());
            }
            String action = updatedUser.isBanned() ? "banned" : "unbanned";
            handler.sendResponse(
                    new SetUserBanResponseMessage("User \"" + updatedUser.getUsername() + "\" was " + action + "."),
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
