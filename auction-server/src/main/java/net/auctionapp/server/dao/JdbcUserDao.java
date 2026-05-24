package net.auctionapp.server.dao;

import net.auctionapp.server.models.users.User;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.services.DatabaseService;
import net.auctionapp.server.utils.UserRoleUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcUserDao implements UserDao {
    private static final String CREATE_USERS_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS users (
                id VARCHAR(64) PRIMARY KEY,
                username VARCHAR(255) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(64) NOT NULL,
                balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
                pending_balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
                is_banned BOOLEAN NOT NULL DEFAULT FALSE
            )
            """;

    private static final String FIND_BY_USERNAME_QUERY =
            "SELECT id, username, password_hash, role, balance, pending_balance, is_banned FROM users WHERE lower(username) = ? LIMIT 1";
    private static final String FIND_BY_ID_QUERY =
            "SELECT id, username, password_hash, role, balance, pending_balance, is_banned FROM users WHERE id = ? LIMIT 1";
    private static final String FIND_ALL_USERS_QUERY =
            "SELECT id, username, password_hash, role, balance, pending_balance, is_banned FROM users ORDER BY username ASC";
    private static final String CREATE_USER_QUERY =
            "INSERT INTO users (id, username, password_hash, role, balance, pending_balance) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_BAN_STATUS_QUERY =
            "UPDATE users SET is_banned = ? WHERE id = ?";
    private static final String INCREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance + ? WHERE id = ?";
    private static final String TRY_DECREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance - ? WHERE id = ? AND balance >= ?";

    private final DatabaseService databaseService;

    public JdbcUserDao() {
        this(DatabaseService.getInstance());
    }

    public JdbcUserDao(DatabaseService databaseService) {
        this.databaseService = databaseService;
        ensureUsersTable();
    }

    private void ensureUsersTable() {
        try (Connection connection = databaseService.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_USERS_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create users table.", e);
        }
    }

    @Override
    public Optional<User> findByUsername(String normalizedUsername) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME_QUERY)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapUser(resultSet));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query user by username.", e);
        }
    }

    @Override
    public Optional<User> findById(String userId) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_QUERY)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(resultSet));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query user by id.", e);
        }
    }

    @Override
    public List<User> findAllUsers() {
        List<User> users = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_USERS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load users.", e);
        }
    }

    @Override
    public boolean updateBanStatus(String userId, boolean banned) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_BAN_STATUS_QUERY)) {
            statement.setBoolean(1, banned);
            statement.setString(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update user ban status.", e);
        }
    }

    @Override
    public boolean createUser(User user) {
        Objects.requireNonNull(user, "user");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_USER_QUERY)) {
            statement.setString(1, user.getId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, UserRoleUtil.toDatabaseRole(user));
            BigDecimal balance = Objects.requireNonNullElse(user.getBalance(), BigDecimal.ZERO);
            statement.setBigDecimal(5, balance);
            BigDecimal pendingBalance = Objects.requireNonNullElse(user.getPendingBalance(), BigDecimal.ZERO);
            statement.setBigDecimal(6, pendingBalance);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create user.", e);
        }
    }

    @Override
    public boolean increaseBalance(String userId, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(INCREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to increase balance.", e);
        }
    }

    @Override
    public boolean tryDecreaseBalance(String userId, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(TRY_DECREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, userId);
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to decrease balance.", e);
        }
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        BigDecimal balance = Objects.requireNonNullElse(resultSet.getBigDecimal("balance"), BigDecimal.ZERO);
        BigDecimal pendingBalance = Objects.requireNonNullElse(
                resultSet.getBigDecimal("pending_balance"),
                BigDecimal.ZERO
        );
        return new User(
                StringUtil.normalizeString(resultSet.getString("id")),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                UserRoleUtil.fromDatabaseRole(resultSet.getString("role")),
                balance,
                pendingBalance,
                resultSet.getBoolean("is_banned")
        );
    }

    private static void requirePositiveMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }
}
