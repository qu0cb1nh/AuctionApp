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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionCriticalTest {
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final BigDecimal STARTING_PRICE = new BigDecimal("100.00");
    private static final BigDecimal MINIMUM_INCREMENT = new BigDecimal("10.00");

    @Test
    void validBidUpdatesPriceLeaderMinimumNextBidAndHistory() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));

        auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(5)));

        assertEquals(new BigDecimal("110.00"), auction.getCurrentPrice());
        assertEquals("bidder-1", auction.getLeadingBidderId());
        assertEquals(new BigDecimal("120.00"), auction.getMinimumNextBid());
        assertEquals(1, auction.getBidHistory().size());
        assertEquals(1, auction.getActiveBidHistory().size());
    }

    @Test
    void secondBidAtExactlyMinimumNextBidIsAccepted() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));

        auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(5)));
        auction.placeBid(bid("bid-2", "bidder-2", "120.00", START_TIME.plusMinutes(6)));

        assertEquals(new BigDecimal("120.00"), auction.getCurrentPrice());
        assertEquals("bidder-2", auction.getLeadingBidderId());
        assertEquals(2, auction.getActiveBidHistory().size());
    }

    @Test
    void bidEqualToCurrentPriceIsRejected() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));

        assertThrows(ValidationException.class, () ->
                auction.placeBid(bid("bid-1", "bidder-1", "100.00", START_TIME.plusMinutes(5))));

        assertEquals(STARTING_PRICE, auction.getCurrentPrice());
        assertNull(auction.getLeadingBidderId());
        assertTrue(auction.getBidHistory().isEmpty());
    }

    @Test
    void bidHigherThanCurrentButBelowMinimumIncrementIsRejected() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));
        auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(5)));

        assertThrows(ValidationException.class, () ->
                auction.placeBid(bid("bid-2", "bidder-2", "119.99", START_TIME.plusMinutes(6))));

        assertEquals(new BigDecimal("110.00"), auction.getCurrentPrice());
        assertEquals("bidder-1", auction.getLeadingBidderId());
        assertEquals(1, auction.getBidHistory().size());
    }

    @Test
    void bidWithNegativeOrZeroAmountIsRejected() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));

        assertThrows(ValidationException.class, () ->
                auction.placeBid(bid("bid-1", "bidder-1", "-10.00", START_TIME.plusMinutes(5))));
        assertThrows(ValidationException.class, () ->
                auction.placeBid(bid("bid-2", "bidder-1", "0.00", START_TIME.plusMinutes(5))));
    }

    @Test
    void bidWithWrongAuctionIdIsRejected() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));
        BidTransaction wrongAuctionBid = new BidTransaction(
                "bid-1",
                new BigDecimal("110.00"),
                START_TIME.plusMinutes(5),
                "bidder-1",
                "other-auction"
        );

        assertThrows(ValidationException.class, () -> auction.placeBid(wrongAuctionBid));
    }

    @Test
    void bidWithoutBidderIsRejected() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));

        assertThrows(ValidationException.class, () ->
                auction.placeBid(bid("bid-1", "", "110.00", START_TIME.plusMinutes(5))));
    }

    @Test
    void sellerCannotBidOnOwnAuction() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));

        assertThrows(ValidationException.class, () ->
                auction.placeBid(bid("bid-1", "seller-1", "110.00", START_TIME.plusMinutes(5))));
    }

    @Test
    void bidBeforeAuctionStartsIsRejected() {
        Auction auction = newAuction(START_TIME.minusMinutes(1));

        assertThrows(InvalidAuctionStateException.class, () ->
                auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.minusMinutes(1))));
    }

    @Test
    void bidAfterAuctionEndsIsRejected() {
        Auction auction = newAuction(START_TIME.plusHours(1));

        assertThrows(InvalidAuctionStateException.class, () ->
                auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusHours(1))));
    }

    @Test
    void bidAfterAuctionIsCanceledIsRejected() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));
        auction.cancel();

        assertThrows(InvalidAuctionStateException.class, () ->
                auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(6))));
    }

    @Test
    void endedAuctionWithoutBidsIsCanceledWithoutWinner() {
        AdjustableClock clock = new AdjustableClock(START_TIME.plusMinutes(5));
        Auction auction = newAuction(clock);

        clock.setTime(START_TIME.plusHours(1));

        assertTrue(auction.closeIfEnded());
        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
        assertNull(auction.getWinnerBidderId());
        assertEquals(STARTING_PRICE, auction.getCurrentPrice());
    }

    @Test
    void manuallyClosedAuctionWithLeadingBidderIsPaid() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));
        auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(5)));

        auction.closeManually();

        assertEquals(AuctionStatus.PAID, auction.getStatus());
        assertEquals("bidder-1", auction.getWinnerBidderId());
    }

    @Test
    void canceledAuctionWithBidsHasNoWinner() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));
        auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(5)));

        auction.cancel();

        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
        assertNull(auction.getWinnerBidderId());
        assertEquals("bidder-1", auction.getLeadingBidderId());
    }

    @Test
    void invalidatingAllActiveBidsResetsPriceAndCancelsAtEnd() {
        AdjustableClock clock = new AdjustableClock(START_TIME.plusMinutes(5));
        Auction auction = newAuction(clock);
        auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(5)));
        auction.placeBid(bid("bid-2", "bidder-2", "120.00", START_TIME.plusMinutes(6)));

        List<BidTransaction> invalidatedForBidderOne = auction.invalidateActiveBidsBy("bidder-1");
        List<BidTransaction> invalidatedForBidderTwo = auction.invalidateActiveBidsBy("bidder-2");
        clock.setTime(START_TIME.plusHours(1));

        assertEquals(1, invalidatedForBidderOne.size());
        assertEquals(1, invalidatedForBidderTwo.size());
        assertEquals(STARTING_PRICE, auction.getCurrentPrice());
        assertNull(auction.getLeadingBidderId());
        assertTrue(auction.closeIfEnded());
        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
    }

    @Test
    void invalidatingUnknownBidderDoesNotChangeAuctionState() {
        Auction auction = newAuction(START_TIME.plusMinutes(5));
        auction.placeBid(bid("bid-1", "bidder-1", "110.00", START_TIME.plusMinutes(5)));

        List<BidTransaction> invalidatedBids = auction.invalidateActiveBidsBy("missing-bidder");

        assertTrue(invalidatedBids.isEmpty());
        assertEquals(new BigDecimal("110.00"), auction.getCurrentPrice());
        assertEquals("bidder-1", auction.getLeadingBidderId());
        assertFalse(auction.getActiveBidHistory().isEmpty());
    }

    private Auction newAuction(LocalDateTime now) {
        return newAuction(new AdjustableClock(now));
    }

    private Auction newAuction(Clock clock) {
        return new Auction(
                "auction-critical-test",
                "seller-1",
                START_TIME,
                START_TIME.plusHours(1),
                new Electronics(
                        "item-critical-test",
                        "Laptop",
                        "Test item",
                        STARTING_PRICE,
                        "Brand",
                        "Model",
                        12
                ),
                STARTING_PRICE,
                MINIMUM_INCREMENT,
                clock
        );
    }

    private BidTransaction bid(String bidId, String bidderId, String amount, LocalDateTime bidTime) {
        return new BidTransaction(
                bidId,
                new BigDecimal(amount),
                bidTime,
                bidderId,
                "auction-critical-test"
        );
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
