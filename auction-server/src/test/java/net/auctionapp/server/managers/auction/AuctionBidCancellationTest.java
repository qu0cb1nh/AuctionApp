package net.auctionapp.server.managers.auction;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.auction.CancelBidsRequestMessage;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.dao.AuctionDao;
import net.auctionapp.server.dao.UserDao;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.WalletManager;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidStatus;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.items.Electronics;
import net.auctionapp.server.models.users.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionBidCancellationTest {
    private static final LocalDateTime START_TIME = LocalDateTime.of(2099, 5, 24, 10, 0);
    private static final Clock CLOCK = Clock.fixed(
            START_TIME.plusMinutes(5).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
    );

    @Mock
    private AuctionDao auctionDao;
    @Mock
    private UserDao userDao;

    private AutoCloseable closeable;
    private AuthManager authManager;
    private AuctionBidCancellation auctionBidCancellation;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        authManager = AuthManager.getInstance();
        authManager.setUserDao(userDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void outbidUserCanCancelBidsAndReleaseLockedFunds() {
        String outbidUserId = "outbid-cancel-user-direct";
        String leaderUserId = "leader-cancel-user-direct";
        Auction auction = auction(outbidUserId, leaderUserId);
        User outbidUser = user(outbidUserId, new BigDecimal("0.00"), new BigDecimal("120.00"));
        loadAuction(auction);
        when(userDao.findById(outbidUserId)).thenReturn(Optional.of(outbidUser));
        when(auctionDao.cancelBids(any(Auction.class), anyList(), anyMap())).thenReturn(true);

        AuctionBidCancellation.CancelBidsResult result = auctionBidCancellation.cancelBids(
                auction.getId(),
                outbidUserId
        );

        assertEquals(1, result.canceledBidCount());
        assertEquals(new BigDecimal("130.00"), auction.getCurrentPrice());
        assertEquals(leaderUserId, auction.getLeadingBidderId());
        assertEquals(List.of(leaderUserId), auction.getActiveBidHistory().stream().map(BidTransaction::getBidderId).toList());
        assertEquals(BidStatus.INVALIDATED, auction.getBidHistory().getFirst().getStatus());
        assertEquals(new BigDecimal("120.00"), outbidUser.getBalance());
        assertEquals(new BigDecimal("0.00"), outbidUser.getPendingBalance());
        verify(auctionDao).cancelBids(
                any(Auction.class),
                argThat(bids -> bids != null && bids.size() == 1 && outbidUserId.equals(bids.getFirst().getBidderId())),
                argThat(funds -> funds != null && new BigDecimal("120.00").equals(funds.get(outbidUserId)))
        );
    }

    @Test
    void leadingBidderCannotCancelBids() {
        String outbidUserId = "outbid-leading-reject-user-direct";
        String leaderUserId = "leader-leading-reject-user-direct";
        Auction auction = auction(outbidUserId, leaderUserId);
        loadAuction(auction);
        when(userDao.findById(leaderUserId)).thenReturn(Optional.of(
                user(leaderUserId, new BigDecimal("0.00"), new BigDecimal("130.00"))
        ));

        assertThrows(ValidationException.class, () -> auctionBidCancellation.cancelBids(auction.getId(), leaderUserId));
        verify(auctionDao, never()).cancelBids(any(Auction.class), anyList(), anyMap());
    }

    @Test
    void cancelBidsRequestRoundTripsThroughJson() {
        Message message = JsonUtil.fromJson(JsonUtil.toJson(new CancelBidsRequestMessage("auction-json")));

        CancelBidsRequestMessage request = assertInstanceOf(CancelBidsRequestMessage.class, message);
        assertEquals("auction-json", request.getAuctionId());
    }

    private void loadAuction(Auction auction) {
        AuctionMutationExecutor auctionMutations = new AuctionMutationExecutor();
        ConcurrentMap<String, Auction> auctions = new ConcurrentHashMap<>();
        when(auctionDao.findAllAuctions()).thenReturn(List.of(auction));
        AuctionPersistence auctionPersistence = new AuctionPersistence(auctions, WalletManager.getInstance());
        auctionPersistence.setAuctionDao(auctionDao);
        auctionBidCancellation = new AuctionBidCancellation(
                auctionMutations,
                authManager,
                WalletManager.getInstance(),
                new AuctionQuery(auctions, authManager, auctionMutations),
                auctionPersistence,
                CLOCK
        );
    }

    private Auction auction(String outbidUserId, String leaderUserId) {
        Auction auction = new Auction(
                "auction-cancel-bids",
                "seller-cancel-bids",
                START_TIME,
                START_TIME.plusMinutes(30),
                new Electronics(
                        "item-cancel-bids",
                        "Laptop",
                        "Test item",
                        new BigDecimal("100.00"),
                        "Brand",
                        "Model",
                        12
                ),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("130.00"),
                leaderUserId,
                null,
                net.auctionapp.common.auction.AuctionStatus.RUNNING
        );
        auction.restoreBidHistory(List.of(
                bid("bid-outbid", outbidUserId, "120.00"),
                bid("bid-leader", leaderUserId, "130.00")
        ));
        return auction;
    }

    private BidTransaction bid(String bidId, String bidderId, String amount) {
        return new BidTransaction(
                bidId,
                new BigDecimal(amount),
                START_TIME.plusMinutes(1),
                bidderId,
                "auction-cancel-bids"
        );
    }

    private User user(String id, BigDecimal balance, BigDecimal pendingBalance) {
        return new User(id, id, "hash", UserRole.USER, balance, pendingBalance, false);
    }
}
