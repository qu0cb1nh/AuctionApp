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
                new AuctionMutationExecutor(),
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
        String auctionId = "auc-1";
        String bidderId = "bidder-1";
        BigDecimal bidAmount = new BigDecimal("150.00");

        Auction auction = createMockAuction(auctionId, new BigDecimal("100.00"));
        when(mockAuctionQuery.requireAuction(auctionId)).thenReturn(auction);
        when(mockWalletManager.getBidderCommitment(any(Auction.class), anyString())).thenReturn(BigDecimal.ZERO);
        when(mockAuthManager.requireActiveUserById(anyString())).thenReturn(mock(User.class));

        AuctionBid.BidResult result = auctionBid.submitBid(auctionId, bidderId, bidAmount);

        assertEquals(bidAmount, result.auction().getCurrentPrice());
        verify(mockWalletManager).applyLockedBidFunds(eq(bidderId), eq(bidAmount));
    }

    @Test
    void submitBid_LowerThanCurrentPrice_ThrowsException() {
        String auctionId = "auc-1";
        Auction auction = createMockAuction(auctionId, new BigDecimal("200.00"));
        when(mockAuctionQuery.requireAuction(auctionId)).thenReturn(auction);

        assertThrows(ValidationException.class, () ->
                auctionBid.submitBid(auctionId, "user-1", new BigDecimal("150.00"))
        );
    }

    private Auction createMockAuction(String id, BigDecimal currentPrice) {
        LocalDateTime now = LocalDateTime.now(fixedClock);
        return new Auction(
                id,
                "seller-1",
                now,
                now.plusHours(1),
                new Electronics(
                        "item-1",
                        "Product Title",
                        "Product Description",
                        currentPrice,
                        "Brand",
                        "Model",
                        12
                ),
                currentPrice,
                new BigDecimal("10.00"),
                fixedClock
        );
    }
}