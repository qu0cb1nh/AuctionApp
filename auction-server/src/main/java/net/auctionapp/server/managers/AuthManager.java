package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auth.ForcedLogoutResponseMessage;
import net.auctionapp.common.messages.auth.LoginRequestMessage;
import net.auctionapp.common.messages.auth.LoginResponseMessage;
import net.auctionapp.common.messages.auth.RegisterRequestMessage;
import net.auctionapp.common.messages.auth.RegisterResponseMessage;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.server.models.users.User;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.CredentialUtil;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.UserDao;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.AuthorizationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.messages.MessageRouter;
import net.auctionapp.server.utils.UserRoleUtil;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthManager.class);
    private static final AuthManager INSTANCE = new AuthManager();
    private static final String INVALID_LOGIN_MESSAGE = "Invalid username or password.";

    private volatile UserDao userDao;
    private final ConcurrentMap<String, User> usersById = new ConcurrentHashMap<>();
    private final SessionManager sessionManager = SessionManager.getInstance();

    private AuthManager() {
    }

    public static AuthManager getInstance() {
        return INSTANCE;
    }

    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    public void registerCommands(MessageRouter messageRouter) {
        messageRouter.register(MessageType.LOGIN_REQUEST, LoginRequestMessage.class, this::handleLogin);
        messageRouter.register(MessageType.REGISTER_REQUEST, RegisterRequestMessage.class, this::handleRegister);
    }

    public void handleLogin(LoginRequestMessage request, ClientHandler clientHandler) {
        String normalizedUsername = StringUtil.normalizeString(request.getUsername());
        String password = request.getPassword();

        try {
            CredentialUtil.validateLogin(request.getUsername(), password);
        } catch (ValidationException e) {
            sendLoginFailure(request, clientHandler, e.getMessage());
            return;
        }

        try {
            Optional<User> storedUser = requireUserDao().findByUsername(normalizedUsername);
            if (storedUser.isEmpty()) {
                sendLoginFailure(request, clientHandler, INVALID_LOGIN_MESSAGE);
                return;
            }

            User user = storedUser.get();
            if (!BCrypt.checkpw(password, user.getPasswordHash())) {
                sendLoginFailure(request, clientHandler, INVALID_LOGIN_MESSAGE);
                return;
            }
            if (user.isBanned()) {
                sendLoginFailure(request, clientHandler, "This account is banned.");
                return;
            }

            cacheUser(user);
            UserRole clientRole = UserRoleUtil.toClientRole(user);
            LoginResponseMessage success = new LoginResponseMessage(
                    MessageType.LOGIN_SUCCESS,
                    user.getId(),
                    user.getUsername(),
                    clientRole,
                    "Login successful."
            );
            clientHandler.authenticate(user.getId(), clientRole);
            sessionManager.bindSession(user.getId(), user.getUsername(), clientRole, clientHandler);
            clientHandler.sendResponse(success, request);
            LOGGER.info("User '{}' logged in successfully.", user.getUsername());
        } catch (DatabaseException e) {
            sendLoginFailure(request, clientHandler, "Cannot connect to authentication database.");
            LOGGER.error("Login query failed for user '{}': {}", normalizedUsername, e.getMessage(), e);
        }
    }

    public void handleRegister(RegisterRequestMessage request, ClientHandler clientHandler) {
        String normalizedUsername = StringUtil.normalizeString(request.getUsername());
        String password = request.getPassword();
        String username = request.getUsername() == null ? "" : request.getUsername().trim();

        try {
            CredentialUtil.validateRegistration(username, password, password);
        } catch (ValidationException e) {
            sendRegisterFailure(request, clientHandler, e.getMessage());
            return;
        }

        try {
            UserDao dao = requireUserDao();
            if (dao.findByUsername(normalizedUsername).isPresent()) {
                sendRegisterFailure(request, clientHandler, "Username already exists.");
                return;
            }

            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            User user = new User(
                    UUID.randomUUID().toString(),
                    username,
                    passwordHash,
                    UserRoleUtil.fromDatabaseRole("user"),
                    false
            );
            if (!dao.createUser(user)) {
                sendRegisterFailure(request, clientHandler, "Registration could not be completed.");
                return;
            }

            cacheUser(user);

            RegisterResponseMessage success = new RegisterResponseMessage(
                    MessageType.REGISTER_SUCCESS,
                    username,
                    "Registration successful. Redirecting..."
            );
            clientHandler.sendResponse(success, request);
            LOGGER.info("New user '{}' registered successfully.", username);
        } catch (DatabaseException e) {
            sendRegisterFailure(request, clientHandler, "Registration failed due to a database error.");
            LOGGER.error("Registration query failed for user '{}': {}", username, e.getMessage(), e);
        }
    }

    private void sendLoginFailure(
            LoginRequestMessage request,
            ClientHandler clientHandler,
            String message
    ) {
        LoginResponseMessage failure = new LoginResponseMessage(MessageType.LOGIN_FAILURE, null, null, null, message);
        clientHandler.sendResponse(failure, request);
    }

    private void sendRegisterFailure(
            RegisterRequestMessage request,
            ClientHandler clientHandler,
            String message
    ) {
        RegisterResponseMessage failure = new RegisterResponseMessage(MessageType.REGISTER_FAILURE, null, message);
        clientHandler.sendResponse(failure, request);
    }

    private UserDao requireUserDao() {
        if (userDao == null) {
            throw new DatabaseException("User persistence is not configured.");
        }
        return userDao;
    }

    public User requireUserById(String userId) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        if (normalizedUserId.isEmpty()) {
            throw new NotFoundException("User not found.");
        }

        User cachedUser = usersById.get(normalizedUserId);
        if (cachedUser != null) {
            return cachedUser;
        }

        Optional<User> userFromDatabase = requireUserDao().findById(normalizedUserId);
        if (userFromDatabase.isEmpty()) {
            throw new NotFoundException("User not found.");
        }
        return cacheUser(userFromDatabase.get());
    }

    public User requireActiveUserById(String userId) {
        User user = requireUserById(userId);
        if (user.isBanned()) {
            throw new AuthenticationException("Your account is banned.");
        }
        return user;
    }

    public User requireAdminUser(String userId) {
        User user = requireActiveUserById(userId);
        if (!user.hasRole(UserRole.ADMIN)) {
            throw new AuthorizationException("Admin privileges are required.");
        }
        return user;
    }

    public List<User> getAllUsers(String actorId) {
        requireAdminUser(actorId);
        List<User> users = requireUserDao().findAllUsers();
        for (User user : users) {
            cacheUser(user);
        }
        return users;
    }

    public User unbanUser(String actorId, String targetUserId) {
        requireAdminUser(actorId);
        User target = requireUserById(targetUserId);
        if (!requireUserDao().clearBan(target.getId())) {
            throw new NotFoundException("User not found.");
        }
        return applyPersistedUserBanStatus(target, false);
    }

    public User validateUserBan(String actorId, String targetUserId) {
        User actor = requireAdminUser(actorId);
        User target = requireUserById(targetUserId);
        if (actor.getId().equals(target.getId())) {
            throw new AuthorizationException("Admin cannot ban own account.");
        }
        if (target.hasRole(UserRole.ADMIN)) {
            throw new AuthorizationException("Admin accounts cannot be banned.");
        }
        return target;
    }

    public User applyPersistedUserBanStatus(User target, boolean banned) {
        target.setBanned(banned);
        cacheUser(target);
        if (banned) {
            logoutBannedUserSessions(target.getId());
        }
        return target;
    }

    private User cacheUser(User user) {
        if (user == null) {
            throw new NotFoundException("User not found.");
        }
        String normalizedUserId = StringUtil.normalizeString(user.getId());
        if (normalizedUserId.isEmpty()) {
            throw new NotFoundException("User not found.");
        }
        return usersById.compute(normalizedUserId, (ignored, existingUser) -> {
            if (existingUser == null) {
                return user;
            }
            existingUser.copyMutableStateFrom(user);
            return existingUser;
        });
    }

    private void logoutBannedUserSessions(String userId) {
        Set<ClientHandler> sessions = sessionManager.getClientsByUserId(userId);
        if (sessions.isEmpty()) {
            return;
        }
        List<ClientHandler> sessionSnapshot = List.copyOf(sessions);
        for (ClientHandler clientHandler : sessionSnapshot) {
            clientHandler.sendMessage(new ForcedLogoutResponseMessage(
                    "Your account has been banned. You have been logged out."
            ));
            clientHandler.logout();
        }
    }
}
