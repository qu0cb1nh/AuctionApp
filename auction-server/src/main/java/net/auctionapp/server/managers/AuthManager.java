package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.models.users.User;
import net.auctionapp.common.utils.CredentialUtil;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.UserDao;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.utils.UserRoleUtil;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class AuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthManager.class);
    private static final AuthManager INSTANCE = new AuthManager();
    private static final String INVALID_LOGIN_MESSAGE = "Invalid username or password.";

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

            userManager.syncAccountFromDatabase(user);
            String clientRole = UserRoleUtil.toClientRole(user);
            LoginResultMessage success = new LoginResultMessage(
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
                    normalizedUsername,
                    username,
                    passwordHash,
                    UserRoleUtil.fromDatabaseRole("user")
            );
            if (!dao.createUser(user)) {
                sendRegisterFailure(request, clientHandler, "Registration could not be completed.");
                return;
            }

            userManager.syncAccountFromDatabase(user);

            RegisterResultMessage success = new RegisterResultMessage(
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

    private void sendLoginFailure(LoginRequestMessage request, ClientHandler clientHandler, String message) {
        LoginResultMessage failure = new LoginResultMessage(MessageType.LOGIN_FAILURE, null, null, null, message);
        clientHandler.sendResponse(failure, request);
    }

    private void sendRegisterFailure(RegisterRequestMessage request, ClientHandler clientHandler, String message) {
        RegisterResultMessage failure = new RegisterResultMessage(MessageType.REGISTER_FAILURE, null, message);
        clientHandler.sendResponse(failure, request);
    }

    private UserDao requireUserDao() {
        if (userDao == null) {
            throw new IllegalStateException("User DAO has not been configured.");
        }
        return userDao;
    }
}
