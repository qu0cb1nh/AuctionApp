package net.auctionapp.server.managers;

import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemResponseMessage;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.managers.auction.AuctionCreation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuctionManagerTest {

    private AuctionManager auctionManager;

    @Mock
    private ClientHandler mockClientHandler;
    @Mock
    private AuthManager mockAuthManager;
    @Mock
    private AuctionCreation mockAuctionCreation;
    @Mock
    private NotificationManager mockNotificationManager;
    @Mock
    private WalletManager mockWalletManager;
    @Mock
    private WatchListManager mockWatchListManager;

    private MockedStatic<AuthManager> mockedAuthManager;
    private MockedStatic<NotificationManager> mockedNotificationManager;
    private MockedStatic<WalletManager> mockedWalletManager;
    private MockedStatic<WatchListManager> mockedWatchListManager;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        try {
            java.lang.reflect.Field instance = AuctionManager.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mockedAuthManager = mockStatic(AuthManager.class);
        mockedAuthManager.when(AuthManager::getInstance).thenReturn(mockAuthManager);

        mockedNotificationManager = mockStatic(NotificationManager.class);
        mockedNotificationManager.when(NotificationManager::getInstance).thenReturn(mockNotificationManager);

        mockedWalletManager = mockStatic(WalletManager.class);
        mockedWalletManager.when(WalletManager::getInstance).thenReturn(mockWalletManager);

        mockedWatchListManager = mockStatic(WatchListManager.class);
        mockedWatchListManager.when(WatchListManager::getInstance).thenReturn(mockWatchListManager);

        auctionManager = AuctionManager.getInstance();

        try {
            java.lang.reflect.Field auctionCreationField = AuctionManager.class.getDeclaredField("auctionCreation");
            auctionCreationField.setAccessible(true);
            auctionCreationField.set(auctionManager, mockAuctionCreation);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) closeable.close();
        if (mockedAuthManager != null) mockedAuthManager.close();
        if (mockedNotificationManager != null) mockedNotificationManager.close();
        if (mockedWalletManager != null) mockedWalletManager.close();
        if (mockedWatchListManager != null) mockedWatchListManager.close();
    }

    @Test
    void testGetInstance_shouldReturnSingletonInstance() {
        assertNotNull(auctionManager, "AuctionManager instance should not be null");
        AuctionManager anotherInstance = AuctionManager.getInstance();
        assertSame(auctionManager, anotherInstance, "Should return the same singleton instance");
    }

    @Test
    void testHandleCreateItem_happyCase_shouldCreateAuctionAndSendSuccessResponse() {
        String userId = "testUser123";
        String title = "Test Item";
        String description = "Description of test item";
        double startPrice = 100.0;
        long endTime = Instant.now().plusSeconds(3600).toEpochMilli();
        String imageUrl = "https://example.com/image.jpg";

        CreateItemRequestMessage requestMessage = new CreateItemRequestMessage(
                title,
                description,
                startPrice,
                endTime,
                imageUrl
        );

        CreateItemResponseMessage expectedResponse = new CreateItemResponseMessage(
                "auction-1",
                title,
                imageUrl,
                "Auction created successfully."
        );

        when(mockClientHandler.getAuthenticatedId()).thenReturn(userId);
        doNothing().when(mockClientHandler).ensureAuthenticated();

        when(mockAuctionCreation.createAuction(any(CreateItemRequestMessage.class), eq(userId)))
                .thenReturn(expectedResponse);

        auctionManager.handleCreateItem(requestMessage, mockClientHandler);

        verify(mockClientHandler, times(1)).ensureAuthenticated();
        verify(mockClientHandler, times(1)).getAuthenticatedId();
        verify(mockAuctionCreation, times(1)).createAuction(requestMessage, userId);
        verify(mockClientHandler, times(1)).sendResponse(eq(expectedResponse), eq(requestMessage));
    }
}