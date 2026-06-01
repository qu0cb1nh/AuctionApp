package net.auctionapp.server.dao;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.server.database.DatabaseConnection;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.items.Electronics;
import net.auctionapp.server.models.users.User;
import net.auctionapp.common.users.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class JdbcBalanceDaoTest {

    private JdbcBalanceDao balanceDao;
    private DatabaseConnection mockDatabaseConnection;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcUrl = "jdbc:h2:mem:balancetestdb_" + UUID.randomUUID().toString() + ";DB_CLOSE_DELAY=-1";

        mockDatabaseConnection = Mockito.mock(DatabaseConnection.class);
        when(mockDatabaseConnection.getConnection()).thenAnswer(invocation -> DriverManager.getConnection(jdbcUrl));

        balanceDao = new JdbcBalanceDao(mockDatabaseConnection);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute(JdbcUserDao.CREATE_USERS_TABLE_QUERY);
            }
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS users");
            }
        }
    }

    private void insertUser(Connection connection, User user) throws SQLException {
        String sql = "INSERT INTO users (id, username, password_hash, role, balance, pending_balance, is_banned) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, user.getRole().name());
            statement.setBigDecimal(5, user.getBalance());
            statement.setBigDecimal(6, user.getPendingBalance());
            statement.setBoolean(7, user.isBanned());
            statement.executeUpdate();
        }
    }

    private BigDecimal getUserBalance(Connection connection, String userId) throws SQLException {
        String sql = "SELECT balance FROM users WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBigDecimal("balance");
                }
            }
        }
        return null;
    }

    private BigDecimal getUserPendingBalance(Connection connection, String userId) throws SQLException {
        String sql = "SELECT pending_balance FROM users WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBigDecimal("pending_balance");
                }
            }
        }
        return null;
    }

    @Test
    void testIncreaseBalance_Success() throws SQLException {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, "testuser", "hash", UserRole.USER, new BigDecimal("100.00"), BigDecimal.ZERO, false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user);
            assertTrue(balanceDao.increaseBalance(userId, new BigDecimal("50.00")));
            assertEquals(new BigDecimal("150.00"), getUserBalance(connection, userId));
        }
    }

    @Test
    void testIncreaseBalance_UserDoesNotExist() throws SQLException {
        String userId = UUID.randomUUID().toString();
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertFalse(balanceDao.increaseBalance(userId, new BigDecimal("50.00")));
        }
    }

    @Test
    void testTryDecreaseBalance_Success() throws SQLException {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, "testuser", "hash", UserRole.USER, new BigDecimal("100.00"), BigDecimal.ZERO, false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user);
            assertTrue(balanceDao.tryDecreaseBalance(userId, new BigDecimal("50.00")));
            assertEquals(new BigDecimal("50.00"), getUserBalance(connection, userId));
        }
    }

    @Test
    void testTryDecreaseBalance_InsufficientFunds() throws SQLException {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, "testuser", "hash", UserRole.USER, new BigDecimal("100.00"), BigDecimal.ZERO, false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user);
            assertFalse(balanceDao.tryDecreaseBalance(userId, new BigDecimal("150.00")));
            assertEquals(new BigDecimal("100.00"), getUserBalance(connection, userId));
        }
    }

    @Test
    void testTryDecreaseBalance_UserDoesNotExist() throws SQLException {
        String userId = UUID.randomUUID().toString();
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertFalse(balanceDao.tryDecreaseBalance(userId, new BigDecimal("50.00")));
        }
    }

    @Test
    void testLockFunds_Success() throws SQLException {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, "testuser", "hash", UserRole.USER, new BigDecimal("100.00"), BigDecimal.ZERO, false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user);
            assertTrue(balanceDao.lockFunds(connection, userId, new BigDecimal("50.00")));
            assertEquals(new BigDecimal("50.00"), getUserBalance(connection, userId));
            assertEquals(new BigDecimal("50.00"), getUserPendingBalance(connection, userId));
        }
    }

    @Test
    void testLockFunds_InsufficientFunds() throws SQLException {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, "testuser", "hash", UserRole.USER, new BigDecimal("100.00"), BigDecimal.ZERO, false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user);
            assertFalse(balanceDao.lockFunds(connection, userId, new BigDecimal("150.00")));
            assertEquals(new BigDecimal("100.00"), getUserBalance(connection, userId));
            assertEquals(new BigDecimal("0.00"), getUserPendingBalance(connection, userId));
        }
    }

    @Test
    void testReleaseFunds_Single_Success() throws SQLException {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, "testuser", "hash", UserRole.USER, new BigDecimal("50.00"), new BigDecimal("50.00"), false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user);
            assertTrue(callReleaseFundsSingle(connection, userId, new BigDecimal("50.00")));
            assertEquals(new BigDecimal("100.00"), getUserBalance(connection, userId));
            assertEquals(new BigDecimal("0.00"), getUserPendingBalance(connection, userId));
        }
    }

    @Test
    void testReleaseFunds_Single_InsufficientPending() throws SQLException {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, "testuser", "hash", UserRole.USER, new BigDecimal("50.00"), new BigDecimal("20.00"), false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user);
            assertFalse(callReleaseFundsSingle(connection, userId, new BigDecimal("50.00")));
            assertEquals(new BigDecimal("50.00"), getUserBalance(connection, userId));
            assertEquals(new BigDecimal("20.00"), getUserPendingBalance(connection, userId));
        }
    }

    private boolean callReleaseFundsSingle(Connection connection, String userId, BigDecimal amount) throws SQLException {
        try {
            java.lang.reflect.Method method = JdbcBalanceDao.class.getDeclaredMethod("releaseFunds", Connection.class, String.class, BigDecimal.class);
            method.setAccessible(true);
            return (boolean) method.invoke(balanceDao, connection, userId, amount);
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    @Test
    void testReleaseFunds_Map_Success() throws SQLException {
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        User user1 = new User(userId1, "user1", "hash", UserRole.USER, new BigDecimal("50.00"), new BigDecimal("50.00"), false);
        User user2 = new User(userId2, "user2", "hash", UserRole.USER, new BigDecimal("30.00"), new BigDecimal("20.00"), false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user1);
            insertUser(connection, user2);

            Map<String, BigDecimal> fundsToRelease = Map.of(
                    userId1, new BigDecimal("50.00"),
                    userId2, new BigDecimal("20.00")
            );

            assertTrue(balanceDao.releaseFunds(connection, fundsToRelease));

            assertEquals(new BigDecimal("100.00"), getUserBalance(connection, userId1));
            assertEquals(new BigDecimal("0.00"), getUserPendingBalance(connection, userId1));
            assertEquals(new BigDecimal("50.00"), getUserBalance(connection, userId2));
            assertEquals(new BigDecimal("0.00"), getUserPendingBalance(connection, userId2));
        }
    }

    @Test
    void testReleaseFunds_Map_PartialFailure() throws SQLException {
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        User user1 = new User(userId1, "user1", "hash", UserRole.USER, new BigDecimal("50.00"), new BigDecimal("50.00"), false);
        User user2 = new User(userId2, "user2", "hash", UserRole.USER, new BigDecimal("30.00"), new BigDecimal("10.00"), false);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertUser(connection, user1);
            insertUser(connection, user2);

            Map<String, BigDecimal> fundsToRelease = Map.of(
                    userId1, new BigDecimal("50.00"),
                    userId2, new BigDecimal("20.00")
            );

            connection.setAutoCommit(false);
            boolean result = balanceDao.releaseFunds(connection, fundsToRelease);
            assertFalse(result);
            connection.rollback();

            assertEquals(new BigDecimal("50.00"), getUserBalance(connection, userId1));
            assertEquals(new BigDecimal("50.00"), getUserPendingBalance(connection, userId1));
            assertEquals(new BigDecimal("30.00"), getUserBalance(connection, userId2));
            assertEquals(new BigDecimal("10.00"), getUserPendingBalance(connection, userId2));
        }
    }
}
