package net.auctionapp.server.dao;

import net.auctionapp.common.models.users.User;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.managers.DatabaseManager;
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
                is_banned BOOLEAN NOT NULL DEFAULT FALSE
            )
            """;

    private static final String FIND_BY_USERNAME_QUERY =
            "SELECT username, password_hash, role, balance, is_banned FROM users WHERE lower(username) = ? LIMIT 1";
    private static final String FIND_ALL_USERS_QUERY =
            "SELECT username, password_hash, role, balance, is_banned FROM users ORDER BY username ASC";
    private static final String CREATE_USER_QUERY =
            "INSERT INTO users (username, password_hash, role, balance) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_BAN_STATUS_QUERY =
            "UPDATE users SET is_banned = ? WHERE lower(username) = ?";
    private static final String INCREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance + ? WHERE lower(username) = ?";
    private static final String TRY_DECREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance - ? WHERE lower(username) = ? AND balance >= ?";

    private final DatabaseManager databaseManager;

    public JdbcUserDao() {
        this(DatabaseManager.getInstance());
    }

    public JdbcUserDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        ensureUsersSchema();
    }

    private void ensureUsersSchema() {
        ensureUsersTable();
        ensureBalanceColumn();
    }

    private void ensureUsersTable() {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_USERS_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create users table.", e);
        }
    }

    private void ensureBalanceColumn() {
        try (Connection connection = databaseManager.getConnection()) {
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

    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        String catalog = connection.getCatalog();
        try (ResultSet columns = connection.getMetaData().getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
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
                BigDecimal balance = resultSet.getBigDecimal("balance");
                if (balance == null) {
                    balance = BigDecimal.ZERO;
                }
                boolean banned = resultSet.getBoolean("is_banned");
                return Optional.of(new User(
                        normalizedUsername,
                        username,
                        passwordHash,
                        UserRoleUtil.fromDatabaseRole(databaseRole),
                        balance,
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
        try (Connection connection = databaseManager.getConnection();
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
                boolean banned = resultSet.getBoolean("is_banned");
                users.add(new User(
                        StringUtil.normalizeString(username),
                        username,
                        passwordHash,
                        UserRoleUtil.fromDatabaseRole(databaseRole),
                        balance,
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
        try (Connection connection = databaseManager.getConnection();
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
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_USER_QUERY)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, UserRoleUtil.toDatabaseRole(user));
            BigDecimal balance = Objects.requireNonNullElse(user.getBalance(), BigDecimal.ZERO);
            statement.setBigDecimal(4, balance);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create user.", e);
        }
    }

    @Override
    public boolean increaseBalance(String normalizedUsername, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseManager.getConnection();
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
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(TRY_DECREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, normalizedUsername);
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to decrease balance.", e);
        }
    }

    private static void requirePositiveMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }
}
