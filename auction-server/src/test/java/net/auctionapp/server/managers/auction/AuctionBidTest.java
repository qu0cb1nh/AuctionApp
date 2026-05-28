package net.auctionapp.server.managers.auction;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.WalletManager;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.items.Electronics;
import net.auctionapp.server.models.users.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuctionBidTest {
    private AuctionBid auctionBid;

    @Mock
    private AuctionSafeUpdateExecutor mockAuctionSafeUpdateExecutor;

    @Mock
    private AuctionQuery mockAuctionQuery;
    @Mock
    private WalletManager mockWalletManager;
    @Mock
    private AuthManager mockAuthManager;
    @Mock
    private AuctionPersistence mockAuctionPersistence;
    private Clock fixedClock;

    private MockedStatic<AuthManager> mockedAuthManager;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        fixedClock = Clock.fixed(Instant.parse("2026-05-24T16:00:00Z"), ZoneId.systemDefault());

        mockedAuthManager = mockStatic(AuthManager.class);
        mockedAuthManager.when(() -> AuthManager.getInstance()).thenReturn(mockAuthManager);

        auctionBid = new AuctionBid(
                mockAuctionSafeUpdateExecutor, // TRUYỀN BIẾN MỚI VÀO ĐÂY
                mockAuthManager,
                mockWalletManager,
                mockAuctionQuery,
                mockAuctionPersistence,
                fixedClock
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) closeable.close();
        if (mockedAuthManager != null) mockedAuthManager.close();
    }

    @Test
    void submitBid_Successful_HappyCase() {

    }
}