package net.auctionapp.server.dao;

import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.common.notifications.NotificationView;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JdbcNotificationDao implements NotificationDao {
    private static final String CREATE_NOTIFICATIONS_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS notifications (
                id VARCHAR(64) PRIMARY KEY,
                user_id VARCHAR(255) NOT NULL,
                type VARCHAR(64) NOT NULL,
                title VARCHAR(255) NOT NULL,
                body TEXT NOT NULL,
                auction_id VARCHAR(64),
                is_read BOOLEAN NOT NULL DEFAULT FALSE,
                created_at DATETIME NOT NULL
            )
            """;
    private static final String INSERT_NOTIFICATION_QUERY = """
            INSERT INTO notifications (id, user_id, type, title, body, auction_id, is_read, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_USER_ID_QUERY = """
            SELECT id, user_id, type, title, body, auction_id, is_read, created_at
            FROM notifications
            WHERE user_id = ?
            ORDER BY created_at DESC
            """;
    private static final String MARK_AS_READ_QUERY = """
            UPDATE notifications
            SET is_read = TRUE
            WHERE id = ? AND user_id = ?
            """;
    private static final String EXISTS_BY_ID_QUERY = """
            SELECT 1
            FROM notifications
            WHERE id = ? AND user_id = ?
            LIMIT 1
            """;
    private static final String CLEAR_BY_ID_QUERY = """
            DELETE FROM notifications
            WHERE id = ? AND user_id = ?
            """;

    private final DatabaseManager databaseManager;

    public JdbcNotificationDao() {
        this(DatabaseManager.getInstance());
    }

    public JdbcNotificationDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        ensureNotificationsTable();
    }

    @Override
    public NotificationView createNotification(
            String userId,
            NotificationType type,
            String title,
            String body,
            String auctionId,
            LocalDateTime createdAt
    ) {
        String id = UUID.randomUUID().toString();
        LocalDateTime timestamp = createdAt == null ? LocalDateTime.now() : createdAt;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_NOTIFICATION_QUERY)) {
            statement.setString(1, id);
            statement.setString(2, userId);
            statement.setString(3, type.name());
            statement.setString(4, title);
            statement.setString(5, body);
            setNullableString(statement, 6, auctionId);
            statement.setBoolean(7, false);
            statement.setTimestamp(8, Timestamp.valueOf(timestamp));
            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new DatabaseException("Notification could not be created.", new IllegalStateException());
            }
            return new NotificationView(id, userId, type, title, body, auctionId, timestamp, false);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create notification.", e);
        }
    }

    @Override
    public List<NotificationView> findByUserId(String userId) {
        List<NotificationView> notifications = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
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
    public boolean markAsRead(String userId, String notificationId) {
        try (Connection connection = databaseManager.getConnection()) {
            if (!existsById(connection, userId, notificationId)) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(MARK_AS_READ_QUERY)) {
                statement.setString(1, notificationId);
                statement.setString(2, userId);
                statement.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to mark notification as read.", e);
        }
    }

    @Override
    public boolean clearById(String userId, String notificationId) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLEAR_BY_ID_QUERY)) {
            statement.setString(1, notificationId);
            statement.setString(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to clear notification.", e);
        }
    }

    private boolean existsById(Connection connection, String userId, String notificationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS_BY_ID_QUERY)) {
            statement.setString(1, notificationId);
            statement.setString(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void ensureNotificationsTable() {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_NOTIFICATIONS_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create notifications table.", e);
        }
    }

    private NotificationView mapNotification(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        if (createdAt == null) {
            throw new DatabaseException("Notification created_at cannot be null.", new IllegalStateException());
        }
        return new NotificationView(
                resultSet.getString("id"),
                resultSet.getString("user_id"),
                NotificationType.valueOf(resultSet.getString("type")),
                resultSet.getString("title"),
                resultSet.getString("body"),
                resultSet.getString("auction_id"),
                createdAt.toLocalDateTime(),
                resultSet.getBoolean("is_read")
        );
    }

    private void setNullableString(PreparedStatement statement, int parameterIndex, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(parameterIndex, java.sql.Types.VARCHAR);
            return;
        }
        statement.setString(parameterIndex, value);
    }
}
