package net.auctionapp.server.dao;

import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.common.notifications.Notification;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.database.DatabaseConnection;
import net.auctionapp.server.utils.JdbcUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JdbcNotificationDao implements NotificationDao {
    public static final String CREATE_NOTIFICATIONS_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS notifications (
                id VARCHAR(64) PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                type VARCHAR(64) NOT NULL,
                title VARCHAR(255) NOT NULL,
                body TEXT NOT NULL,
                auction_id VARCHAR(64),
                created_at DATETIME NOT NULL
            )
            """;
    private static final String INSERT_NOTIFICATION_QUERY = """
            INSERT INTO notifications (id, user_id, type, title, body, auction_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_USER_ID_QUERY = """
            SELECT id, user_id, type, title, body, auction_id, created_at
            FROM notifications
            WHERE user_id = ?
            ORDER BY created_at DESC
            """;
    private static final String CLEAR_BY_ID_QUERY = """
            DELETE FROM notifications
            WHERE id = ? AND user_id = ?
            """;
    private static final String CLEAR_BY_USER_ID_QUERY = """
            DELETE FROM notifications
            WHERE user_id = ?
            """;

    private final DatabaseConnection databaseConnection;

    public JdbcNotificationDao() {
        this(DatabaseConnection.getInstance());
    }

    public JdbcNotificationDao(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
        JdbcUtil.ensureTable(databaseConnection, CREATE_NOTIFICATIONS_TABLE_QUERY, "notifications");
    }

    @Override
    public Notification createNotification(
            String userId,
            NotificationType type,
            String title,
            String body,
            String auctionId,
            LocalDateTime createdAt
    ) {
        String id = UUID.randomUUID().toString();
        LocalDateTime timestamp = createdAt == null ? LocalDateTime.now() : createdAt;
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_NOTIFICATION_QUERY)) {
            statement.setString(1, id);
            statement.setString(2, userId);
            statement.setString(3, type.name());
            statement.setString(4, title);
            statement.setString(5, body);
            JdbcUtil.setNullableString(statement, 6, auctionId);
            statement.setTimestamp(7, Timestamp.valueOf(timestamp));
            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new DatabaseException("Notification could not be created.");
            }
            return new Notification(id, userId, type, title, body, auctionId, timestamp);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create notification.", e);
        }
    }

    @Override
    public List<Notification> findByUserId(String userId) {
        List<Notification> notifications = new ArrayList<>();
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USER_ID_QUERY)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    notifications.add(mapNotification(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load notifications.", e);
        }
        return notifications;
    }

    @Override
    public boolean clearById(String userId, String notificationId) {
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLEAR_BY_ID_QUERY)) {
            statement.setString(1, notificationId);
            statement.setString(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to clear notification.", e);
        }
    }

    @Override
    public void clearByUserId(String userId) {
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLEAR_BY_USER_ID_QUERY)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to clear notifications.", e);
        }
    }

    private Notification mapNotification(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        if (createdAt == null) {
            throw new DatabaseException("Notification created_at cannot be null.");
        }
        return new Notification(
                resultSet.getString("id"),
                resultSet.getString("user_id"),
                NotificationType.valueOf(resultSet.getString("type")),
                resultSet.getString("title"),
                resultSet.getString("body"),
                resultSet.getString("auction_id"),
                createdAt.toLocalDateTime()
        );
    }

}
