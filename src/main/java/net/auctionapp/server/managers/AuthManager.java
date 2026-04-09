package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.utils.CredentialUtil;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.database.DatabaseManager;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class AuthManager {
    private static final String LOGIN_QUERY = "SELECT username, password_hash, role FROM users WHERE lower(username) = ? LIMIT 1";
    private static final String CHECK_USERNAME_QUERY = "SELECT 1 FROM users WHERE lower(username) = ? LIMIT 1";
    private static final String REGISTER_QUERY = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
    private static final String DEFAULT_ROLE = "user";
    private static final AuthManager INSTANCE = new AuthManager();
    private final UserManager userManager = UserManager.getInstance();

    private AuthManager() {
    }

    public static AuthManager getInstance() {
        return INSTANCE;
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

        try (Connection connection = DatabaseManager.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(LOGIN_QUERY)) {

            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    sendLoginFailure(clientHandler, "Wrong username.");
                    return;
                }

                String storedHash = resultSet.getString("password_hash");
                if (!BCrypt.checkpw(password, storedHash)) {
                    sendLoginFailure(clientHandler, "Wrong password.");
                    return;
                }

                String username = resultSet.getString("username");
                String role = resultSet.getString("role");
                userManager.syncAccountFromDatabase(username, storedHash, role);
                LoginResultMessage success = new LoginResultMessage(
                        MessageType.LOGIN_SUCCESS,
                        username,
                        role,
                        "Login successful."
                );
                clientHandler.authenticate(username, role);
                clientHandler.sendMessage(JsonUtil.toJson(success));
            }
        } catch (SQLException e) {
            sendLoginFailure(clientHandler, "Cannot connect to authentication database.");
            System.err.println("Login query failed: " + e.getMessage());
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

        try (Connection connection = DatabaseManager.getInstance().getConnection()) {
            if (usernameExists(connection, normalizedUsername)) {
                sendRegisterFailure(clientHandler, "Username already exists.");
                return;
            }

            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            try (PreparedStatement statement = connection.prepareStatement(REGISTER_QUERY)) {
                statement.setString(1, username);
                statement.setString(2, passwordHash);
                statement.setString(3, DEFAULT_ROLE);

                int inserted = statement.executeUpdate();
                if (inserted != 1) {
                    sendRegisterFailure(clientHandler, "Registration could not be completed.");
                    return;
                }
            }

            userManager.syncAccountFromDatabase(username, passwordHash, DEFAULT_ROLE);

            RegisterResultMessage success = new RegisterResultMessage(
                    MessageType.REGISTER_SUCCESS,
                    username,
                    "Registration successful. Redirecting..."
            );
            clientHandler.sendMessage(JsonUtil.toJson(success));
        } catch (SQLException e) {
            sendRegisterFailure(clientHandler, "Registration failed due to a database error.");
            System.err.println("Registration query failed: " + e.getMessage());
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

    private boolean usernameExists(Connection connection, String normalizedUsername) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHECK_USERNAME_QUERY)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
