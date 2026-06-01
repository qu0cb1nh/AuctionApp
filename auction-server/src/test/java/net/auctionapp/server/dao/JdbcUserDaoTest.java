package net.auctionapp.server.dao;

import net.auctionapp.server.database.DatabaseConnection;
import net.auctionapp.server.models.users.User;

import net.auctionapp.common.users.UserRole;
import net.auctionapp.server.exceptions.DatabaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class JdbcUserDaoTest {

    private JdbcUserDao userDao;
    private DatabaseConnection mockDatabaseConnection;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws SQLException {

        jdbcUrl = "jdbc:h2:mem:testdb_" + UUID.randomUUID().toString() + ";DB_CLOSE_DELAY=-1";

        mockDatabaseConnection = Mockito.mock(DatabaseConnection.class);

        when(mockDatabaseConnection.getConnection()).thenAnswer(invocation -> DriverManager.getConnection(jdbcUrl));

        userDao = new JdbcUserDao(mockDatabaseConnection);

        
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute(JdbcUserDao.CREATE_USERS_TABLE_QUERY);
            }
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        // Dọn dẹp bảng sau khi test xong để không ảnh hưởng bài test khác
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS users");
            }
        }
    }

    @Test
    void testCreateUser() {
        User newUser = new User(
                UUID.randomUUID().toString(),
                "testuser",
                "passwordhash",
                UserRole.USER,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false
        );
        assertTrue(userDao.createUser(newUser));

        Optional<User> foundUser = userDao.findByUsername("testuser");
        assertTrue(foundUser.isPresent());
        assertEquals(newUser.getUsername(), foundUser.get().getUsername());
        assertEquals(newUser.getId(), foundUser.get().getId());
    }

    @Test
    void testFindByUsername_UserExists() {
        User user1 = new User(UUID.randomUUID().toString(), "user1", "hash1", UserRole.USER, BigDecimal.ZERO, BigDecimal.ZERO, false);
        userDao.createUser(user1);

        Optional<User> found = userDao.findByUsername("user1");
        assertTrue(found.isPresent());
        assertEquals(user1.getUsername(), found.get().getUsername());
    }

    @Test
    void testFindByUsername_UserDoesNotExist() {
        Optional<User> found = userDao.findByUsername("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    void testFindById_UserExists() {
        User user1 = new User(UUID.randomUUID().toString(), "user1", "hash1", UserRole.USER, BigDecimal.ZERO, BigDecimal.ZERO, false);
        userDao.createUser(user1);

        Optional<User> found = userDao.findById(user1.getId());
        assertTrue(found.isPresent());
        assertEquals(user1.getId(), found.get().getId());
    }

    @Test
    void testFindById_UserDoesNotExist() {
        Optional<User> found = userDao.findById(UUID.randomUUID().toString());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAllUsers_NoUsers() {
        List<User> users = userDao.findAllUsers();
        assertTrue(users.isEmpty());
    }

    @Test
    void testFindAllUsers_MultipleUsers() {
        User user1 = new User(UUID.randomUUID().toString(), "alpha", "hash1", UserRole.USER, BigDecimal.ZERO, BigDecimal.ZERO, false);
        User user2 = new User(UUID.randomUUID().toString(), "beta", "hash2", UserRole.ADMIN, BigDecimal.ZERO, BigDecimal.ZERO, false);
        userDao.createUser(user1);
        userDao.createUser(user2);

        List<User> users = userDao.findAllUsers();
        assertEquals(2, users.size());

        assertEquals("alpha", users.get(0).getUsername());
        assertEquals("beta", users.get(1).getUsername());
    }

    @Test
    void testClearBan_UserIsBanned() {
        User bannedUser = new User(UUID.randomUUID().toString(), "banned", "hash", UserRole.USER, BigDecimal.ZERO, BigDecimal.ZERO, true);
        userDao.createUser(bannedUser);

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             var statement = connection.prepareStatement("UPDATE users SET is_banned = TRUE WHERE id = ?")) {
            statement.setString(1, bannedUser.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            fail("Failed to ban user for test setup: " + e.getMessage());
        }

        assertTrue(userDao.findById(bannedUser.getId()).get().isBanned());

        assertTrue(userDao.clearBan(bannedUser.getId()));

        assertFalse(userDao.findById(bannedUser.getId()).get().isBanned());
    }

    @Test
    void testClearBan_UserIsNotBanned() {
        User user = new User(UUID.randomUUID().toString(), "notbanned", "hash", UserRole.USER, BigDecimal.ZERO, BigDecimal.ZERO, false);
        userDao.createUser(user);

        assertFalse(userDao.findById(user.getId()).get().isBanned());

        assertTrue(userDao.clearBan(user.getId()));

        assertFalse(userDao.findById(user.getId()).get().isBanned());
    }

    @Test
    void testClearBan_UserDoesNotExist() {
        assertFalse(userDao.clearBan(UUID.randomUUID().toString()));
    }

    @Test
    void testCreateUser_DuplicateUsername() {
        User user1 = new User(UUID.randomUUID().toString(), "duplicate", "hash1", UserRole.USER, BigDecimal.ZERO, BigDecimal.ZERO, false);
        userDao.createUser(user1);

        User user2 = new User(UUID.randomUUID().toString(), "duplicate", "hash2", UserRole.USER, BigDecimal.ZERO, BigDecimal.ZERO, false);

        assertThrows(DatabaseException.class, () -> userDao.createUser(user2));
    }
}