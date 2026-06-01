package net.auctionapp.server.dao;

import net.auctionapp.server.database.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class JdbcWatchListDaoTest {

    private JdbcWatchListDao watchListDao;
    private DatabaseConnection mockDatabaseConnection;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcUrl = "jdbc:h2:mem:watchlisttestdb_" + UUID.randomUUID().toString() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";

        mockDatabaseConnection = Mockito.mock(DatabaseConnection.class);
        when(mockDatabaseConnection.getConnection()).thenAnswer(invocation -> DriverManager.getConnection(jdbcUrl));

        watchListDao = new JdbcWatchListDao(mockDatabaseConnection);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute(JdbcWatchListDao.CREATE_WATCH_LIST_TABLE_QUERY);
                statement.execute(JdbcWatchListDao.CREATE_WATCH_LIST_REMINDERS_TABLE_QUERY);
            }
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS watch_list_reminders");
                statement.execute("DROP TABLE IF EXISTS watch_list");
            }
        }
    }

    private void insertWatchListEntry(Connection connection, String userId, String auctionId, LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO watch_list (user_id, auction_id, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, auctionId);
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        }
    }

    private boolean reminderExists(Connection connection, String userId, String auctionId, String reminderType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM watch_list_reminders WHERE user_id = ? AND auction_id = ? AND reminder_type = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, auctionId);
            statement.setString(3, reminderType);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    @Test
    void testSetWatched_Add() throws SQLException {
        String userId = UUID.randomUUID().toString();
        String auctionId = UUID.randomUUID().toString();

        watchListDao.setWatched(userId, auctionId, true);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            String sql = "SELECT COUNT(*) FROM watch_list WHERE user_id = ? AND auction_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, userId);
                statement.setString(2, auctionId);
                try (ResultSet rs = statement.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 1);
                }
            }
        }
    }

    @Test
    void testSetWatched_Remove() throws SQLException {
        String userId = UUID.randomUUID().toString();
        String auctionId = UUID.randomUUID().toString();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertWatchListEntry(connection, userId, auctionId, LocalDateTime.now());
        }

        watchListDao.setWatched(userId, auctionId, false);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            String sql = "SELECT COUNT(*) FROM watch_list WHERE user_id = ? AND auction_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, userId);
                statement.setString(2, auctionId);
                try (ResultSet rs = statement.executeQuery()) {
                    assertTrue(rs.next() && rs.getInt(1) == 0);
                }
            }
        }
    }

    @Test
    void testFindAuctionIdsByUserId_NoAuctions() {
        String userId = UUID.randomUUID().toString();
        List<String> auctionIds = watchListDao.findAuctionIdsByUserId(userId);
        assertTrue(auctionIds.isEmpty());
    }

    @Test
    void testFindAuctionIdsByUserId_MultipleAuctions() throws SQLException {
        String userId = UUID.randomUUID().toString();
        String auctionId1 = UUID.randomUUID().toString();
        String auctionId2 = UUID.randomUUID().toString();
        String auctionId3 = UUID.randomUUID().toString();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertWatchListEntry(connection, userId, auctionId1, LocalDateTime.now().minusHours(2));
            insertWatchListEntry(connection, userId, auctionId2, LocalDateTime.now().minusHours(1));
            insertWatchListEntry(connection, userId, auctionId3, LocalDateTime.now());
        }

        List<String> auctionIds = watchListDao.findAuctionIdsByUserId(userId);
        assertEquals(3, auctionIds.size());

        assertEquals(auctionId3, auctionIds.get(0));
        assertEquals(auctionId2, auctionIds.get(1));
        assertEquals(auctionId1, auctionIds.get(2));
    }

    @Test
    void testClaimEndingSoonReminderRecipients_NoWatchers() throws SQLException {
        String auctionId = UUID.randomUUID().toString();
        List<String> recipients = watchListDao.claimEndingSoonReminderRecipients(auctionId);
        assertTrue(recipients.isEmpty());
    }

    @Test
    void testClaimEndingSoonReminderRecipients_MultipleWatchers() throws SQLException {
        String auctionId = UUID.randomUUID().toString();
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        String userId3 = UUID.randomUUID().toString();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertWatchListEntry(connection, userId1, auctionId, LocalDateTime.now());
            insertWatchListEntry(connection, userId2, auctionId, LocalDateTime.now());
            insertWatchListEntry(connection, userId3, auctionId, LocalDateTime.now());
        }

        List<String> recipients = watchListDao.claimEndingSoonReminderRecipients(auctionId);
        assertEquals(3, recipients.size());
        assertTrue(recipients.contains(userId1));
        assertTrue(recipients.contains(userId2));
        assertTrue(recipients.contains(userId3));

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertTrue(reminderExists(connection, userId1, auctionId, "ENDING_SOON"));
            assertTrue(reminderExists(connection, userId2, auctionId, "ENDING_SOON"));
            assertTrue(reminderExists(connection, userId3, auctionId, "ENDING_SOON"));
        }
    }

    @Test
    void testClaimEndingSoonReminderRecipients_AlreadyClaimed() throws SQLException {
        String auctionId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            insertWatchListEntry(connection, userId, auctionId, LocalDateTime.now());

            String sql = "INSERT INTO watch_list_reminders (user_id, auction_id, reminder_type, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, userId);
                statement.setString(2, auctionId);
                statement.setString(3, "ENDING_SOON");
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
                statement.executeUpdate();
            }
        }

        List<String> recipients = watchListDao.claimEndingSoonReminderRecipients(auctionId);
        assertTrue(recipients.isEmpty());
    }
}