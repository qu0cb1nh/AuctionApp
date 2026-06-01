package net.auctionapp.server.models.auction;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.models.items.Electronics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AuctionCriticalTest {

    private Auction newAuction(LocalDateTime startTime, LocalDateTime endTime, Clock clock, String sellerId, BigDecimal startingPrice, BigDecimal minimumBidIncrement) {
        return new Auction(
                "auction-critical-test",
                sellerId,
                startTime,
                endTime,
                new Electronics(
                        "item-critical-test",
                        "Test Item",
                        "Description for test item",
                        startingPrice,
                        "BrandX",
                        "ModelY",
                        12
                ),
                startingPrice,
                minimumBidIncrement,
                clock
        );
    }

    private Auction newAuction(LocalDateTime startTime, LocalDateTime endTime, Clock clock, String sellerId, BigDecimal startingPrice, BigDecimal minimumBidIncrement, AuctionStatus status) {
        return new Auction(
                "auction-critical-test",
                sellerId,
                startTime,
                endTime,
                new Electronics(
                        "item-critical-test",
                        "Test Item",
                        "Description for test item",
                        startingPrice,
                        "BrandX",
                        "ModelY",
                        12
                ),
                startingPrice,
                minimumBidIncrement,
                startingPrice,
                null,
                null,
                status,
                clock
        );
    }

    private BidTransaction createBid(String bidId, String bidderId, BigDecimal amount, LocalDateTime bidTime) {
        return new BidTransaction(bidId, amount, bidTime, bidderId, "auction-critical-test");
    }

    @Test
    void placeBidRejectsNegativeAmount() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        assertThrows(ValidationException.class, () -> auction.placeBid(
                createBid("bid-1", "bidder-1", new BigDecimal("-10.00"), startTime.plusMinutes(15))
        ), "Should reject bids with negative amounts.");
    }

    @Test
    void placeBidRejectsZeroAmount() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        assertThrows(ValidationException.class, () -> auction.placeBid(
                createBid("bid-1", "bidder-1", BigDecimal.ZERO, startTime.plusMinutes(15))
        ), "Should reject bids with zero amount.");
    }

    @Test
    void placeBidRejectsAmountLowerThanCurrentPrice() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        auction.placeBid(createBid("bid-1", "bidder-1", new BigDecimal("110.00"), startTime.plusMinutes(15)));
        assertEquals(new BigDecimal("110.00"), auction.getCurrentPrice());

        assertThrows(ValidationException.class, () -> auction.placeBid(
                createBid("bid-2", "bidder-2", new BigDecimal("105.00"), startTime.plusMinutes(20))
        ), "Should reject bids lower than the current price.");
    }

    @Test
    void placeBidRejectsAmountLowerThanMinimumIncrementFromCurrentPrice() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        auction.placeBid(createBid("bid-1", "bidder-1", new BigDecimal("110.00"), startTime.plusMinutes(15)));
        assertEquals(new BigDecimal("110.00"), auction.getCurrentPrice());

        assertThrows(ValidationException.class, () -> auction.placeBid(
                createBid("bid-2", "bidder-2", new BigDecimal("115.00"), startTime.plusMinutes(20))
        ), "Should reject bids that are not at least currentPrice + minimumBidIncrement.");
    }

    @Test
    void placeBidRejectsWhenAuctionHasNotStarted() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.minusMinutes(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        assertThrows(InvalidAuctionStateException.class, () -> auction.placeBid(
                createBid("bid-1", "bidder-1", new BigDecimal("110.00"), startTime.minusMinutes(5))
        ), "Should reject bids if the auction has not started.");
    }

    @Test
    void placeBidRejectsBySeller() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        String sellerId = "seller-1";
        Auction auction = newAuction(startTime, endTime, clock, sellerId, new BigDecimal("100.00"), new BigDecimal("10.00"));

        assertThrows(ValidationException.class, () -> auction.placeBid(
                createBid("bid-1", sellerId, new BigDecimal("110.00"), startTime.plusMinutes(15))
        ), "Should reject bids placed by the seller of the auction.");
    }

    @Test
    void createAuctionRejectsStartTimeAfterEndTime() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.minusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "new-seller-id", startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                new BigDecimal("50.00"), new BigDecimal("5.00"), clock
        ));
    }

    @Test
    void createAuctionRejectsNegativeStartingPrice() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "new-seller-id", startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                new BigDecimal("-10.00"), new BigDecimal("5.00"), clock
        ));
    }

    @Test
    void createAuctionRejectsZeroStartingPrice() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "new-seller-id", startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                BigDecimal.ZERO, new BigDecimal("5.00"), clock
        ));
    }

    @Test
    void createAuctionRejectsNegativeMinimumBidIncrement() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "new-seller-id", startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                new BigDecimal("50.00"), new BigDecimal("-5.00"), clock
        ));
    }

    @Test
    void createAuctionRejectsZeroMinimumBidIncrement() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "new-seller-id", startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                new BigDecimal("50.00"), BigDecimal.ZERO, clock
        ));
    }

    @Test
    void createAuctionRejectsNullSellerId() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", null, startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                new BigDecimal("50.00"), new BigDecimal("5.00"), clock
        ));
    }

    @Test
    void createAuctionRejectsEmptySellerId() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "", startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                new BigDecimal("50.00"), new BigDecimal("5.00"), clock
        ));
    }

    @Test
    void createAuctionRejectsNullItem() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "new-seller-id", startTime, endTime, null,
                new BigDecimal("50.00"), new BigDecimal("5.00"), clock
        ));
    }

    @Test
    void createAuctionRejectsStartTimeInThePast() {
        LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(LocalDateTime.now().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        assertDoesNotThrow(() -> new Auction(
                "new-auction-id", "new-seller-id", startTime, endTime,
                new Electronics("new-item-id", "New Gadget", "A brand new gadget", new BigDecimal("50.00"), "TechBrand", "ModelX", 6),
                new BigDecimal("50.00"), new BigDecimal("5.00"), clock
        ));
    }

    @Test
    void updateAuctionDetailsRejectsEndTimeInPast() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime originalEndTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, originalEndTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        LocalDateTime newEndTime = startTime.minusHours(1);

        assertThrows(ValidationException.class, () -> auction.updateManagedListingDetails(
                "Updated title", "Updated description", newEndTime
        ), "Should reject updating endTime to a time in the past.");
    }

    @Test
    void updateAuctionDetailsRejectsEndTimeBeforeStartTime() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime originalEndTime = startTime.plusHours(2);
        Clock clock = Clock.fixed(startTime.plusMinutes(30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, originalEndTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        LocalDateTime newEndTime = startTime.minusMinutes(1);

        assertThrows(ValidationException.class, () -> auction.updateManagedListingDetails(
                "Updated title", "Updated description", newEndTime
        ), "Should reject updating endTime to a time before startTime.");
    }

    @Test
    void updateAuctionDetailsRejectsUpdateAfterAuctionEnded() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(endTime.plusMinutes(1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.PAID);

        assertThrows(InvalidAuctionStateException.class, () -> auction.updateManagedListingDetails(
                "Updated title", "Updated description", endTime.plusHours(1)
        ), "Should reject updating auction details after the auction has ended.");
    }

    @Test
    void updateAuctionDetailsRejectsUpdateAfterAuctionCancelled() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.CANCELED);

        assertThrows(InvalidAuctionStateException.class, () -> auction.updateManagedListingDetails(
                "Updated title", "Updated description", endTime.plusHours(1)
        ), "Should reject updating auction details after the auction has been cancelled.");
    }

    @Test
    void cancelAuctionRejectsIfAlreadyEnded() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(endTime.plusMinutes(1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.PAID);

        assertThrows(InvalidAuctionStateException.class, auction::cancel, "Should reject cancelling an auction that has already ended.");
    }

    @Test
    void cancelAuctionRejectsIfAlreadyCancelled() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.CANCELED);

        assertThrows(InvalidAuctionStateException.class, auction::cancel, "Should reject cancelling an auction that has already been cancelled.");
    }

    @Test
    void closeIfEndedNoBids() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(endTime.plusMinutes(1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.RUNNING);


        assertTrue(auction.closeIfEnded());
        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
        assertNull(auction.getWinnerBidderId(), "Winner bidder ID should be null.");
        assertEquals(new BigDecimal("100.00"), auction.getCurrentPrice());
    }

    @Test
    void closeIfEndedAllBidsInvalidated() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        AdjustableClock clock = new AdjustableClock(startTime.plusMinutes(15));
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.RUNNING);

        auction.placeBid(createBid("bid-1", "bidder-1", new BigDecimal("110.00"), startTime.plusMinutes(10)));
        auction.placeBid(createBid("bid-2", "bidder-2", new BigDecimal("120.00"), startTime.plusMinutes(20)));

        auction.invalidateActiveBidsBy("bidder-1");
        auction.invalidateActiveBidsBy("bidder-2");

        clock.setTime(endTime.plusMinutes(1));

        assertTrue(auction.closeIfEnded());

        assertEquals(AuctionStatus.CANCELED, auction.getStatus());

        assertNull(auction.getWinnerBidderId(), "Winner bidder ID should be null.");
        assertEquals(new BigDecimal("100.00"), auction.getCurrentPrice());
    }

    @Test
    void invalidateActiveBidsByNonExistentBidder() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        Clock clock = Clock.fixed(startTime.plusMinutes(30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        Auction auction = newAuction(startTime, endTime, clock, "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"));

        auction.placeBid(createBid("bid-1", "bidder-1", new BigDecimal("110.00"), startTime.plusMinutes(10)));
        auction.placeBid(createBid("bid-2", "bidder-2", new BigDecimal("120.00"), startTime.plusMinutes(20)));

        List<BidTransaction> invalidatedBids = auction.invalidateActiveBidsBy("non-existent-bidder");

        assertTrue(invalidatedBids.isEmpty(), "Should return an empty list if no bids were invalidated.");
        assertEquals(new BigDecimal("120.00"), auction.getCurrentPrice(), "Current price should remain unchanged.");
        assertEquals("bidder-2", auction.getLeadingBidderId(), "Leading bidder should remain unchanged.");
        assertEquals(2, auction.getActiveBidHistory().size(), "Active bid history size should remain unchanged.");
    }


    private static final class AdjustableClock extends Clock {
        private LocalDateTime time;

        private AdjustableClock(LocalDateTime time) {
            this.time = time;
        }

        private void setTime(LocalDateTime time) {
            this.time = time;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return time.toInstant(ZoneOffset.UTC);
        }
    }
}