package net.auctionapp.server.controllers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
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

    private void sendLoginFailure(ClientHandler clientHandler, String message) {
        LoginResultMessage failure = new LoginResultMessage(MessageType.LOGIN_FAILURE, null, null, message);
        clientHandler.sendMessage(JsonUtil.toJson(failure));
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}

