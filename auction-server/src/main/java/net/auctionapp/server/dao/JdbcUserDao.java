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
                username VARCHAR(255) PRIMARY KEY,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(64) NOT NULL,
                balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
                pending_balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
                is_banned BOOLEAN NOT NULL DEFAULT FALSE
            )
            """;

    private static final String FIND_BY_USERNAME_QUERY =
            "SELECT username, password_hash, role, balance, pending_balance, is_banned FROM users WHERE lower(username) = ? LIMIT 1";
    private static final String FIND_ALL_USERS_QUERY =
            "SELECT username, password_hash, role, balance, pending_balance, is_banned FROM users ORDER BY username ASC";
    private static final String CREATE_USER_QUERY =
            "INSERT INTO users (username, password_hash, role, balance, pending_balance) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_BAN_STATUS_QUERY =
            "UPDATE users SET is_banned = ? WHERE lower(username) = ?";
    private static final String INCREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance + ? WHERE lower(username) = ?";
    private static final String TRY_DECREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance - ? WHERE lower(username) = ? AND balance >= ?";
    private static final String LOCK_FUNDS_QUERY =
            "UPDATE users SET balance = balance - ?, pending_balance = pending_balance + ? WHERE lower(username) = ? AND balance >= ?";
    private static final String RELEASE_FUNDS_QUERY =
            "UPDATE users SET balance = balance + ?, pending_balance = pending_balance - ? WHERE lower(username) = ? AND pending_balance >= ?";
    private static final String TRANSFER_PENDING_QUERY =
            "UPDATE users SET pending_balance = pending_balance - ? WHERE lower(username) = ? AND pending_balance >= ?";

    private final DatabaseService databaseService;

    public JdbcUserDao() {
        this(DatabaseService.getInstance());
    }

    public JdbcUserDao(DatabaseService databaseService) {
        this.databaseService = databaseService;
        ensureUsersSchema();
    }

    private void ensureUsersSchema() {
        ensureUsersTable();
        ensureBalanceColumn();
        ensurePendingBalanceColumn();
    }

    private void ensureUsersTable() {
        try (Connection connection = databaseService.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_USERS_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create users table.", e);
        }
    }

    private void ensureBalanceColumn() {
        try (Connection connection = databaseService.getConnection()) {
            if (columnExists(connection, "users", "balance")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE users ADD COLUMN balance DECIMAL(19, 2) NOT NULL DEFAULT 0");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to add balance column to users table.", e);
        }
    }

    private void ensurePendingBalanceColumn() {
        try (Connection connection = databaseService.getConnection()) {
            if (columnExists(connection, "users", "pending_balance")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE users ADD COLUMN pending_balance DECIMAL(19, 2) NOT NULL DEFAULT 0");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to add pending_balance column to users table.", e);
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        String catalog = connection.getCatalog();
        try (ResultSet columns = connection.getMetaData().getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
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

                String username = resultSet.getString("username");
                String passwordHash = resultSet.getString("password_hash");
                String databaseRole = resultSet.getString("role");
                BigDecimal balance = resultSet.getBigDecimal("balance");
                if (balance == null) {
                    balance = BigDecimal.ZERO;
                }
                BigDecimal pendingBalance = resultSet.getBigDecimal("pending_balance");
                if (pendingBalance == null) {
                    pendingBalance = BigDecimal.ZERO;
                }
                boolean banned = resultSet.getBoolean("is_banned");
                return Optional.of(new User(
                        normalizedUsername,
                        username,
                        passwordHash,
                        UserRoleUtil.fromDatabaseRole(databaseRole),
                        balance,
                        pendingBalance,
                        banned
                ));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query user by username.", e);
        }
    }

    @Override
    public List<User> findAllUsers() {
        List<User> users = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_USERS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                String passwordHash = resultSet.getString("password_hash");
                String databaseRole = resultSet.getString("role");
                BigDecimal balance = resultSet.getBigDecimal("balance");
                if (balance == null) {
                    balance = BigDecimal.ZERO;
                }
                BigDecimal pendingBalance = resultSet.getBigDecimal("pending_balance");
                if (pendingBalance == null) {
                    pendingBalance = BigDecimal.ZERO;
                }
                boolean banned = resultSet.getBoolean("is_banned");
                users.add(new User(
                        StringUtil.normalizeString(username),
                        username,
                        passwordHash,
                        UserRoleUtil.fromDatabaseRole(databaseRole),
                        balance,
                        pendingBalance,
                        banned
                ));
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load users.", e);
        }
    }

    @Override
    public boolean updateBanStatus(String normalizedUsername, boolean banned) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_BAN_STATUS_QUERY)) {
            statement.setBoolean(1, banned);
            statement.setString(2, normalizedUsername);
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
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, UserRoleUtil.toDatabaseRole(user));
            BigDecimal balance = Objects.requireNonNullElse(user.getBalance(), BigDecimal.ZERO);
            statement.setBigDecimal(4, balance);
            BigDecimal pendingBalance = Objects.requireNonNullElse(user.getPendingBalance(), BigDecimal.ZERO);
            statement.setBigDecimal(5, pendingBalance);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create user.", e);
        }
    }

    @Override
    public boolean increaseBalance(String normalizedUsername, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(INCREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, normalizedUsername);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to increase balance.", e);
        }
    }

    @Override
    public boolean tryDecreaseBalance(String normalizedUsername, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(TRY_DECREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, normalizedUsername);
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to decrease balance.", e);
        }
    }

    public boolean lockFunds(String normalizedUsername, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOCK_FUNDS_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setBigDecimal(2, amount);
            statement.setString(3, normalizedUsername);
            statement.setBigDecimal(4, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to lock funds.", e);
        }
    }

    public boolean releaseFunds(String normalizedUsername, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(RELEASE_FUNDS_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setBigDecimal(2, amount);
            statement.setString(3, normalizedUsername);
            statement.setBigDecimal(4, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to release funds.", e);
        }
    }

    public boolean transferPendingFunds(String normalizedUsername, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(TRANSFER_PENDING_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, normalizedUsername);
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to transfer pending funds.", e);
        }
    }

    private static void requirePositiveMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }
}
