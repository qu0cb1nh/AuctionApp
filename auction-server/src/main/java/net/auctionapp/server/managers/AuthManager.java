package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;
import net.auctionapp.common.utils.CredentialUtil;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.UserDao;
import net.auctionapp.server.exceptions.DatabaseException;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

public class AuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthManager.class);
    private static final AuthManager INSTANCE = new AuthManager();

    private volatile UserDao userDao;
    private final UserManager userManager = UserManager.getInstance();
    private final SessionManager sessionManager = SessionManager.getInstance();

    private AuthManager() {
    }

    public static AuthManager getInstance() {
        return INSTANCE;
    }

    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    public void handleLogin(LoginRequestMessage request, ClientHandler clientHandler) {
        String normalizedUsername = normalizeUsername(request.getUsername());
        String password = request.getPassword();

        try {
            CredentialUtil.validateLogin(request.getUsername(), password);
        } catch (ValidationException e) {
            sendLoginFailure(clientHandler, e.getMessage());
            return;
        }

        try {
            Optional<User> storedUser = requireUserDao().findByUsername(normalizedUsername);
            if (storedUser.isEmpty()) {
                sendLoginFailure(clientHandler, "Wrong username.");
                return;
            }

            User user = storedUser.get();
            if (!BCrypt.checkpw(password, user.getPasswordHash())) {
                sendLoginFailure(clientHandler, "Wrong password.");
                return;
            }

            userManager.syncAccountFromDatabase(user);
            String clientRole = toClientRole(user);
            LoginResultMessage success = new LoginResultMessage(
                    MessageType.LOGIN_SUCCESS,
                    user.getUsername(),
                    clientRole,
                    "Login successful."
            );
            clientHandler.authenticate(user.getId(), clientRole);
            sessionManager.bindSession(user.getId(), user.getUsername(), clientRole, clientHandler);
            clientHandler.sendMessage(JsonUtil.toJson(success));
            LOGGER.info("User '{}' logged in successfully.", user.getUsername());
        } catch (DatabaseException e) {
            sendLoginFailure(clientHandler, "Cannot connect to authentication database.");
            LOGGER.error("Login query failed for user '{}': {}", normalizedUsername, e.getMessage(), e);
        }
    }

    public void handleRegister(RegisterRequestMessage request, ClientHandler clientHandler) {
        String normalizedUsername = normalizeUsername(request.getUsername());
        String password = request.getPassword();
        String username = request.getUsername() == null ? "" : request.getUsername().trim();

        try {
            CredentialUtil.validateRegistration(username, password, password);
        } catch (ValidationException e) {
            sendRegisterFailure(clientHandler, e.getMessage());
            return;
        }

        try {
            UserDao dao = requireUserDao();
            if (dao.findByUsername(normalizedUsername).isPresent()) {
                sendRegisterFailure(clientHandler, "Username already exists.");
                return;
            }

            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            User user = new User(
                    normalizedUsername,
                    username,
                    passwordHash,
                    EnumSet.of(UserRole.SELLER, UserRole.BIDDER)
            );
            if (!dao.createUser(user)) {
                sendRegisterFailure(clientHandler, "Registration could not be completed.");
                return;
            }

            userManager.syncAccountFromDatabase(user);

            RegisterResultMessage success = new RegisterResultMessage(
                    MessageType.REGISTER_SUCCESS,
                    username,
                    "Registration successful. Redirecting..."
            );
            clientHandler.sendMessage(JsonUtil.toJson(success));
            LOGGER.info("New user '{}' registered successfully.", username);
        } catch (DatabaseException e) {
            sendRegisterFailure(clientHandler, "Registration failed due to a database error.");
            LOGGER.error("Registration query failed for user '{}': {}", username, e.getMessage(), e);
        }
    }

    private void sendLoginFailure(ClientHandler clientHandler, String message) {
        LoginResultMessage failure = new LoginResultMessage(MessageType.LOGIN_FAILURE, null, null, message);
        clientHandler.sendMessage(JsonUtil.toJson(failure));
    }

    private void sendRegisterFailure(ClientHandler clientHandler, String message) {
        RegisterResultMessage failure = new RegisterResultMessage(MessageType.REGISTER_FAILURE, null, message);
        clientHandler.sendMessage(JsonUtil.toJson(failure));
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private UserDao requireUserDao() {
        if (userDao == null) {
            throw new IllegalStateException("User DAO has not been configured.");
        }
        return userDao;
    }

    private String toClientRole(User user) {
        return user.hasRole(UserRole.ADMIN) ? "admin" : "user";
    }
}
