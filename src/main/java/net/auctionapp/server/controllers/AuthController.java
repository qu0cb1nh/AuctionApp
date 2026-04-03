package net.auctionapp.server.controllers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;
import net.auctionapp.common.utils.DatabaseUtil;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.ClientHandler;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class AuthController {
    private static final String LOGIN_QUERY = "SELECT username, password_hash, role FROM users WHERE LOWER(username) = ? LIMIT 1";
    private static final String CHECK_USERNAME_QUERY = "SELECT 1 FROM users WHERE LOWER(username) = ? LIMIT 1";
    private static final String REGISTER_QUERY = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
    private static final String DEFAULT_ROLE = "user";
    private static final AuthController INSTANCE = new AuthController();

    private AuthController() {
    }

    public static AuthController getInstance() {
        return INSTANCE;
    }

    public void handleLogin(LoginRequestMessage request, ClientHandler clientHandler) {
        String normalizedUsername = normalizeUsername(request.getUsername());
        String rawPassword = request.getPassword();

        if (normalizedUsername.isEmpty() || rawPassword == null || rawPassword.isEmpty()) {
            sendLoginFailure(clientHandler, "Username and password are required.");
            return;
        }

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOGIN_QUERY)) {

            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    sendLoginFailure(clientHandler, "Invalid username or password.");
                    return;
                }

                String storedHash = resultSet.getString("password_hash");
                if (!BCrypt.checkpw(rawPassword, storedHash)) {
                    sendLoginFailure(clientHandler, "Invalid username or password.");
                    return;
                }

                String canonicalUsername = resultSet.getString("username");
                String role = resultSet.getString("role");
                LoginResultMessage success = new LoginResultMessage(
                        MessageType.LOGIN_SUCCESS,
                        canonicalUsername,
                        role,
                        "Login successful."
                );
                clientHandler.sendMessage(JsonUtil.toJson(success));
            }
        } catch (SQLException e) {
            sendLoginFailure(clientHandler, "Cannot connect to authentication database.");
            System.err.println("Login query failed: " + e.getMessage());
        }
    }

    public void handleRegister(RegisterRequestMessage request, ClientHandler clientHandler) {
        String normalizedUsername = normalizeUsername(request.getUsername());
        String rawPassword = request.getPassword();
        String canonicalUsername = request.getUsername() == null ? "" : request.getUsername().trim();

        if (normalizedUsername.isEmpty() || rawPassword == null || rawPassword.isEmpty()) {
            sendRegisterFailure(clientHandler, "Username and password are required.");
            return;
        }

        try (Connection connection = DatabaseUtil.getConnection()) {
            if (usernameExists(connection, normalizedUsername)) {
                sendRegisterFailure(clientHandler, "Username already exists.");
                return;
            }

            String passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
            try (PreparedStatement statement = connection.prepareStatement(REGISTER_QUERY)) {
                statement.setString(1, canonicalUsername);
                statement.setString(2, passwordHash);
                statement.setString(3, DEFAULT_ROLE);

                int inserted = statement.executeUpdate();
                if (inserted != 1) {
                    sendRegisterFailure(clientHandler, "Registration could not be completed.");
                    return;
                }
            }

            RegisterResultMessage success = new RegisterResultMessage(
                    MessageType.REGISTER_SUCCESS,
                    canonicalUsername,
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
