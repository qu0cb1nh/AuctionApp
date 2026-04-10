package net.auctionapp.server.dao;

import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;

public class JdbcUserDao implements UserDao {
    private static final String FIND_BY_USERNAME_QUERY =
            "SELECT username, password_hash, role FROM users WHERE lower(username) = ? LIMIT 1";
    private static final String CREATE_USER_QUERY =
            "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";

    private final DatabaseManager databaseManager;

    public JdbcUserDao() {
        this(DatabaseManager.getInstance());
    }

    public JdbcUserDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<User> findByUsername(String normalizedUsername) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME_QUERY)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                String username = resultSet.getString("username");
                String passwordHash = resultSet.getString("password_hash");
                String databaseRole = resultSet.getString("role");
                return Optional.of(new User(
                        normalizedUsername,
                        username,
                        passwordHash,
                        resolveRoles(databaseRole)
                ));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query user by username.", e);
        }
    }

    @Override
    public boolean createUser(User user) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_USER_QUERY)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, toDatabaseRole(user));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create user.", e);
        }
    }

    private EnumSet<UserRole> resolveRoles(String databaseRole) {
        if ("admin".equalsIgnoreCase(databaseRole)) {
            return EnumSet.of(UserRole.ADMIN, UserRole.SELLER, UserRole.BIDDER);
        }
        return EnumSet.of(UserRole.SELLER, UserRole.BIDDER);
    }

    private String toDatabaseRole(User user) {
        return user.hasRole(UserRole.ADMIN) ? "admin" : "user";
    }
}
