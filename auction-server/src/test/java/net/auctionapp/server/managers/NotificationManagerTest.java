package net.auctionapp.server.managers;

import net.auctionapp.common.messages.notification.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.notification.NotificationsResponseMessage;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.NotificationDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationManagerTest {
    private static final String USER_ID = "user-1";

    private NotificationManager notificationManager;

    @Mock
    private NotificationDao notificationDao;
    @Mock
    private ClientHandler clientHandler;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        notificationManager = NotificationManager.getInstance();
        notificationManager.setNotificationDao(notificationDao);
        when(clientHandler.getAuthenticatedId()).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() throws Exception {
        notificationManager.setNotificationDao(null);
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void clearAllDeletesEveryNotificationForAuthenticatedUser() {
        ClearNotificationsRequestMessage request = new ClearNotificationsRequestMessage(true);
        when(notificationDao.findByUserId(USER_ID)).thenReturn(List.of());

        notificationManager.handleClearNotifications(request, clientHandler);

        verify(notificationDao).clearByUserId(USER_ID);
        verify(notificationDao, never()).clearById(anyString(), anyString());
        ArgumentCaptor<NotificationsResponseMessage> responseCaptor =
                ArgumentCaptor.forClass(NotificationsResponseMessage.class);
        verify(clientHandler).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals(0, responseCaptor.getValue().getNotifications().size());
    }
}
