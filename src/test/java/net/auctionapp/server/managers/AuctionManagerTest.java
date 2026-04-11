package net.auctionapp.server.managers;

import net.auctionapp.common.models.auction.Auction;
import net.auctionapp.common.models.auction.AuctionStatus;
import net.auctionapp.common.models.auction.BidTransaction;
import net.auctionapp.common.models.items.Electronics;
import net.auctionapp.common.models.items.Item;
import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.exceptions.InvalidBidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class AuctionManagerTest {
    private UserManager userManager;
    private AuctionManager auctionManager;

    @BeforeEach
    void setUp() {
        userManager = UserManager.getInstance();
        auctionManager = AuctionManager.getInstance();
        userManager.clear();
        auctionManager.clear();
    }

    @Test
    void shouldRegisterAndLoginRegularUsersWithBothSellerAndBidderContexts() {
        User seller = userManager.registerUser("seller1", "secret1");
        User bidder = userManager.registerUser("bidder1", "secret2");

        User loggedInSeller = userManager.login("seller1", "secret1");
        User loggedInBidder = userManager.login("bidder1", "secret2");

        assertFalse(seller.hasRole(UserRole.ADMIN));
        assertFalse(bidder.hasRole(UserRole.ADMIN));
        assertEquals(seller.getId(), loggedInSeller.getId());
        assertEquals(bidder.getId(), loggedInBidder.getId());
        assertNotNull(userManager.requireSeller(seller.getId()));
        assertNotNull(userManager.requireBidder(seller.getId()));
    }

    @Test
    void shouldRejectInvalidLogin() {
        userManager.registerUser("seller1", "secret1");

        assertThrows(AuthenticationException.class, () -> userManager.login("seller1", "wrong-pass"));
    }

    @Test
    void shouldCreateEditAndDeleteAuction() {
        User seller = userManager.registerUser("seller1", "secret1");
        User admin = userManager.registerAdmin("admin1", "secret2");
        Item item = new Electronics(
                "item-1",
                "Phone",
                "Good phone",
                new BigDecimal("100.00"),
                "Apple",
                "iPhone",
                12
        );

        Auction auction = auctionManager.createAuction(
                seller.getId(),
                item,
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusHours(1)
        );

        Auction updated = auctionManager.updateAuction(
                seller.getId(),
                auction.getId(),
                "Phone Pro",
                "Updated description",
                new BigDecimal("120.00"),
                new BigDecimal("10.00"),
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusHours(2)
        );

        assertEquals("Phone Pro", updated.getItem().getTitle());
        assertEquals(new BigDecimal("120.00"), updated.getCurrentPrice());

        auctionManager.deleteAuction(admin.getId(), auction.getId());
        assertTrue(auctionManager.getAuctionById(auction.getId()).isEmpty());
    }

    @Test
    void shouldUseDatabaseRoleToMarkAdminInMemory() {
        User admin = userManager.syncAccountFromDatabase(new User(
                "root",
                "root",
                "$2a$10$abcdefghijklmnopqrstuv",
                EnumSet.of(UserRole.ADMIN, UserRole.SELLER, UserRole.BIDDER)
        ));
        User user = userManager.syncAccountFromDatabase(new User(
                "user1",
                "user1",
                "$2a$10$abcdefghijklmnopqrstuv",
                EnumSet.of(UserRole.SELLER, UserRole.BIDDER)
        ));

        assertTrue(admin.hasRole(UserRole.ADMIN));
        assertFalse(user.hasRole(UserRole.ADMIN));
        assertNotNull(userManager.requireSeller(admin.getId()));
        assertNotNull(userManager.requireBidder(admin.getId()));
    }

    @Test
    void shouldAcceptValidBidAndTrackWinner() {
        User seller = userManager.registerUser("seller1", "secret1");
        User bidder = userManager.registerUser("bidder1", "secret2");
        Item item = new Electronics(
                "item-1",
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500.00"),
                "Dell",
                "G15",
                24
        );

        Auction auction = auctionManager.createAuction(
                seller.getId(),
                item,
                new BigDecimal("500.00"),
                new BigDecimal("20.00"),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        BidTransaction bid = auctionManager.submitBid(auction.getId(), bidder.getId(), new BigDecimal("520.00"));

        assertNotNull(bid);
        assertEquals(new BigDecimal("520.00"), auction.getCurrentPrice());
        assertEquals(bidder.getId(), auction.getLeadingBidderId());
    }

    @Test
    void shouldRejectBidLowerThanRequiredPrice() {
        User seller = userManager.registerUser("seller1", "secret1");
        User bidder = userManager.registerUser("bidder1", "secret2");
        Item item = new Electronics(
                "item-1",
                "Camera",
                "Mirrorless camera",
                new BigDecimal("300.00"),
                "Sony",
                "A7",
                12
        );

        Auction auction = auctionManager.createAuction(
                seller.getId(),
                item,
                new BigDecimal("300.00"),
                new BigDecimal("25.00"),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        assertThrows(
                InvalidBidException.class,
                () -> auctionManager.submitBid(auction.getId(), bidder.getId(), new BigDecimal("310.00"))
        );
    }

    @Test
    void shouldFinishAndMarkAuctionPaid() {
        User seller = userManager.registerUser("seller1", "secret1");
        User bidder = userManager.registerUser("bidder1", "secret2");
        Item item = new Electronics(
                "item-1",
                "Watch",
                "Smart watch",
                new BigDecimal("150.00"),
                "Samsung",
                "Galaxy Watch",
                12
        );

        Auction auction = auctionManager.createAuction(
                seller.getId(),
                item,
                new BigDecimal("150.00"),
                new BigDecimal("10.00"),
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(5)
        );

        auctionManager.submitBid(auction.getId(), bidder.getId(), new BigDecimal("160.00"));
        auctionManager.finishAuction(auction.getId());
        auctionManager.markAuctionPaid(auction.getId());

        assertEquals(AuctionStatus.PAID, auction.getStatus());
        assertEquals(bidder.getId(), auction.getWinnerBidderId());
    }

    @Test
    void adminShouldAlsoBeAbleToSellAndBid() {
        User admin = userManager.registerAdmin("admin1", "secret2");
        User user = userManager.registerUser("user1", "secret1");
        Item adminItem = new Electronics(
                "item-1",
                "Console",
                "Game console",
                new BigDecimal("250.00"),
                "Sony",
                "PS5",
                12
        );
        Item userItem = new Electronics(
                "item-2",
                "Headphones",
                "Noise cancelling headphones",
                new BigDecimal("100.00"),
                "Sony",
                "WH-1000XM5",
                12
        );

        Auction adminAuction = auctionManager.createAuction(
                admin.getId(),
                adminItem,
                new BigDecimal("250.00"),
                new BigDecimal("10.00"),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );
        Auction userAuction = auctionManager.createAuction(
                user.getId(),
                userItem,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10)
        );

        BidTransaction bidOnAdminAuction = auctionManager.submitBid(adminAuction.getId(), user.getId(), new BigDecimal("260.00"));
        BidTransaction bidByAdmin = auctionManager.submitBid(userAuction.getId(), admin.getId(), new BigDecimal("110.00"));

        assertNotNull(bidOnAdminAuction);
        assertNotNull(bidByAdmin);
        assertEquals(new BigDecimal("260.00"), adminAuction.getCurrentPrice());
        assertEquals(user.getId(), adminAuction.getLeadingBidderId());
        assertEquals(new BigDecimal("110.00"), userAuction.getCurrentPrice());
        assertEquals(admin.getId(), userAuction.getLeadingBidderId());
    }

    @Test
    void shouldRejectBidWhenAuctionHasFinished() {
        User seller = userManager.registerUser("seller1", "secret1");
        User bidder = userManager.registerUser("bidder1", "secret2");
        Item item = new Electronics(
                "item-1",
                "Tablet",
                "Android tablet",
                new BigDecimal("200.00"),
                "Lenovo",
                "Tab",
                12
        );

        Auction auction = auctionManager.createAuction(
                seller.getId(),
                item,
                new BigDecimal("200.00"),
                new BigDecimal("10.00"),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().minusMinutes(1)
        );

        auctionManager.refreshAuctionStatuses();
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
        assertThrows(
                InvalidAuctionStateException.class,
                () -> auctionManager.submitBid(auction.getId(), bidder.getId(), new BigDecimal("210.00"))
        );
    }
}
