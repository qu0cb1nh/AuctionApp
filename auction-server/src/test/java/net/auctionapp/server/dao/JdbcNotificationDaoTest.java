package net.auctionapp.server.dao;

import net.auctionapp.common.notifications.Notification;
import net.auctionapp.common.notifications.NotificationType;
import net.auctionapp.server.database.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
public class JdbcNotificationDaoTest {

    private JdbcNotificationDao notificationDao;
    private DatabaseConnection mockDatabaseConnection;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcUrl = "jdbc:h2:mem:notificationtestdb_" + UUID.randomUUID().toString() + ";DB_CLOSE_DELAY=-1";

        mockDatabaseConnection = Mockito.mock(DatabaseConnection.class);
        when(mockDatabaseConnection.getConnection()).thenAnswer(invocation -> DriverManager.getConnection(jdbcUrl));

        notificationDao = new JdbcNotificationDao(mockDatabaseConnection);

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute(JdbcNotificationDao.CREATE_NOTIFICATIONS_TABLE_QUERY);
            }
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS notifications");
            }
        }
    }

    private Notification insertNotification(String userId, NotificationType type, String title, String body, String auctionId, LocalDateTime createdAt) throws SQLException {
        Notification notification = notificationDao.createNotification(userId, type, title, body, auctionId, createdAt);
        assertNotNull(notification);
        return notification;
    }

    @Test
    void testCreateNotification_Success() throws SQLException {
        String userId = UUID.randomUUID().toString();
        String auctionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        Notification notification = notificationDao.createNotification(
                userId,
                NotificationType.OUTBID,
                "New Bid!",
                "A new bid was placed on your auction.",
                auctionId,
                now
        );

        assertNotNull(notification);
        assertNotNull(notification.getId());
        assertEquals(userId, notification.getUserId());
        assertEquals(NotificationType.OUTBID, notification.getType());
        assertEquals("New Bid!", notification.getTitle());
        assertEquals("A new bid was placed on your auction.", notification.getBody());
        assertEquals(auctionId, notification.getAuctionId());
        assertEquals(now.withNano(0), notification.getCreatedAt().withNano(0));

        List<Notification> foundNotifications = notificationDao.findByUserId(userId);
        assertEquals(1, foundNotifications.size());
        assertEquals(notification.getId(), foundNotifications.get(0).getId());
    }

    @Test
    void testCreateNotification_NullAuctionId() throws SQLException {
        String userId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        Notification notification = notificationDao.createNotification(
                userId,
                NotificationType.WATCH_LIST_ENDING_SOON,
                "Account Alert",
                "Your account needs attention.",
                null,
                now
        );

        assertNotNull(notification);
        assertNull(notification.getAuctionId());

        List<Notification> foundNotifications = notificationDao.findByUserId(userId);
        assertEquals(1, foundNotifications.size());
        assertNull(foundNotifications.get(0).getAuctionId());
    }

    @Test
    void testFindByUserId_NoNotifications() {
        String userId = UUID.randomUUID().toString();
        List<Notification> notifications = notificationDao.findByUserId(userId);
        assertTrue(notifications.isEmpty());
    }

    @Test
    void testFindByUserId_MultipleNotifications() throws SQLException {
        String userId = UUID.randomUUID().toString();
        String auctionId1 = UUID.randomUUID().toString();
        String auctionId2 = UUID.randomUUID().toString();

        Notification notif1 = insertNotification(userId, NotificationType.OUTBID, "Bid 1", "Body 1", auctionId1, LocalDateTime.now().minusHours(2));
        Notification notif2 = insertNotification(userId, NotificationType.AUCTION_WON, "Won!", "You won auction 2", auctionId2, LocalDateTime.now().minusHours(1));
        Notification notif3 = insertNotification(userId, NotificationType.WATCH_LIST_ENDING_SOON, "Alert", "Account issue", null, LocalDateTime.now());

        insertNotification(UUID.randomUUID().toString(), NotificationType.OUTBID, "Other Bid", "Other body", UUID.randomUUID().toString(), LocalDateTime.now());

        List<Notification> notifications = notificationDao.findByUserId(userId);
        assertEquals(3, notifications.size());

        assertEquals(notif3.getId(), notifications.get(0).getId());
        assertEquals(notif2.getId(), notifications.get(1).getId());
        assertEquals(notif1.getId(), notifications.get(2).getId());
    }

    @Test
    void testClearById_Success() throws SQLException {
        String userId = UUID.randomUUID().toString();
        Notification notification = insertNotification(userId, NotificationType.OUTBID, "Title", "Body", UUID.randomUUID().toString(), LocalDateTime.now());

        assertTrue(notificationDao.clearById(userId, notification.getId()));

        List<Notification> foundNotifications = notificationDao.findByUserId(userId);
        assertTrue(foundNotifications.isEmpty());
    }

    @Test
    void testClearById_NotificationNotFound() throws SQLException {
        String userId = UUID.randomUUID().toString();
        insertNotification(userId, NotificationType.OUTBID, "Title", "Body", UUID.randomUUID().toString(), LocalDateTime.now());

        assertFalse(notificationDao.clearById(userId, UUID.randomUUID().toString()));

        List<Notification> foundNotifications = notificationDao.findByUserId(userId);
        assertEquals(1, foundNotifications.size());
    }

    @Test
    void testClearById_WrongUser() throws SQLException {
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        Notification notification = insertNotification(userId1, NotificationType.OUTBID, "Title", "Body", UUID.randomUUID().toString(), LocalDateTime.now());

        assertFalse(notificationDao.clearById(userId2, notification.getId()));

        List<Notification> foundNotifications = notificationDao.findByUserId(userId1);
        assertEquals(1, foundNotifications.size());
    }

    @Test
    void testClearByUserId_Success() throws SQLException {
        String userId = UUID.randomUUID().toString();
        insertNotification(userId, NotificationType.OUTBID, "Title 1", "Body 1", UUID.randomUUID().toString(), LocalDateTime.now());
        insertNotification(userId, NotificationType.AUCTION_WON, "Title 2", "Body 2", UUID.randomUUID().toString(), LocalDateTime.now());

        String otherUserId = UUID.randomUUID().toString();
        insertNotification(otherUserId, NotificationType.WATCH_LIST_ENDING_SOON, "Other User", "Other Body", null, LocalDateTime.now());

        notificationDao.clearByUserId(userId);

        List<Notification> foundNotifications = notificationDao.findByUserId(userId);
        assertTrue(foundNotifications.isEmpty());

        List<Notification> otherUserNotifications = notificationDao.findByUserId(otherUserId);
        assertEquals(1, otherUserNotifications.size());
        assertEquals("Other User", otherUserNotifications.get(0).getTitle());
    }

    @Test
    void testClearByUserId_NoNotifications() {
        String userId = UUID.randomUUID().toString();
        notificationDao.clearByUserId(userId);
        List<Notification> foundNotifications = notificationDao.findByUserId(userId);
        assertTrue(foundNotifications.isEmpty());
    }
}