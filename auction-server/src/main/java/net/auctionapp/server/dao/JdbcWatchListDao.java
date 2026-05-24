package net.auctionapp.server.dao;

import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.services.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcWatchListDao implements WatchListDao {
    private static final String CREATE_WATCH_LIST_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS watch_list (
                user_id VARCHAR(255) NOT NULL,
                auction_id VARCHAR(64) NOT NULL,
                created_at DATETIME NOT NULL,
                PRIMARY KEY (user_id, auction_id),
                INDEX idx_watch_list_user_created_at (user_id, created_at),
                INDEX idx_watch_list_auction_id (auction_id)
            )
            """;
    private static final String FIND_BY_USER_ID_QUERY = """
            SELECT auction_id
            FROM watch_list
            WHERE user_id = ?
            ORDER BY created_at DESC
            """;
    private static final String ADD_TO_WATCH_LIST_QUERY = """
            INSERT IGNORE INTO watch_list (user_id, auction_id, created_at)
            VALUES (?, ?, ?)
            """;
    private static final String REMOVE_FROM_WATCH_LIST_QUERY = """
            DELETE FROM watch_list
            WHERE user_id = ? AND auction_id = ?
            """;

    private final DatabaseService databaseService;

    public JdbcWatchListDao() {
        this(DatabaseService.getInstance());
    }

    public JdbcWatchListDao(DatabaseService databaseService) {
        this.databaseService = databaseService;
        ensureWatchListTable();
    }

    @Override
    public List<String> findAuctionIdsByUserId(String userId) {
        List<String> auctionIds = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USER_ID_QUERY)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    auctionIds.add(resultSet.getString("auction_id"));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load watch list.", e);
        }
        return auctionIds;
    }

    @Override
    public void setWatched(String userId, String auctionId, boolean watched) {
        String query = watched ? ADD_TO_WATCH_LIST_QUERY : REMOVE_FROM_WATCH_LIST_QUERY;
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, userId);
            statement.setString(2, auctionId);
            if (watched) {
                statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update watch list.", e);
        }
    }

    private void ensureWatchListTable() {
        try (Connection connection = databaseService.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_WATCH_LIST_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create watch list table.", e);
        }
    }
}
