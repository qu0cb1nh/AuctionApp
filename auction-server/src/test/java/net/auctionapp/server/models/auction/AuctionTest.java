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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuctionTest {
    @Test
    void placeBidExtendsEndTimeWhenBidArrivesInsideAntiSnipeWindow() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime endTime = startTime.plusMinutes(1);
        Auction auction = newAuction(startTime, endTime, endTime.minusSeconds(5));

        auction.placeBid(
                new BidTransaction("bid-1", new BigDecimal("110.00"), endTime.minusSeconds(5), "bidder-1", "auction-1")
        );

        assertEquals(endTime.plusSeconds(60), auction.getEndTime());
    }

    @Test
    void placeBidKeepsEndTimeWhenBidArrivesOutsideAntiSnipeWindow() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime endTime = startTime.plusMinutes(1);
        Auction auction = newAuction(startTime, endTime, endTime.minusSeconds(31));

        auction.placeBid(
                new BidTransaction("bid-1", new BigDecimal("110.00"), endTime.minusSeconds(31), "bidder-1", "auction-1")
        );

        assertEquals(endTime, auction.getEndTime());
    }

    @Test
    void placeBidRejectsPriceBelowMinimumIncrement() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        Auction auction = newAuction(startTime, startTime.plusMinutes(10), startTime.plusMinutes(1));

        assertThrows(ValidationException.class, () -> auction.placeBid(
                new BidTransaction("bid-1", new BigDecimal("109.00"), startTime.plusMinutes(1), "bidder-1", "auction-1")
        ));
    }

    @Test
    void placeBidRejectsBidAfterAuctionEnds() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime endTime = startTime.plusMinutes(10);
        Auction auction = newAuction(startTime, endTime, endTime);

        assertThrows(InvalidAuctionStateException.class, () -> auction.placeBid(
                new BidTransaction("bid-1", new BigDecimal("110.00"), endTime, "bidder-1", "auction-1")
        ));
    }

    @Test
    void applySnapshotMakesCandidateManagedChangesVisibleOnlyAfterCommit() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 25, 10, 0);
        Auction auction = newAuction(startTime, startTime.plusMinutes(10), startTime.minusMinutes(1));
        Auction candidate = auction.snapshotCopy();

        candidate.updateManagedListingDetails(
                "Updated laptop",
                "Updated description",
                startTime.plusMinutes(12)
        );

        assertEquals("Laptop", auction.getItem().getTitle());

        auction.applySnapshot(candidate);

        assertEquals("Updated laptop", auction.getItem().getTitle());
        assertEquals(new BigDecimal("100.00"), auction.getStartingPrice());
        assertEquals(new BigDecimal("10.00"), auction.getMinimumBidIncrement());
    }

    @Test
    void itemCopiesKeepConcreteElectronicsAttributes() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 25, 10, 0);
        Auction auction = newAuction(startTime, startTime.plusMinutes(10), startTime);

        Electronics copiedItem = (Electronics) auction.getItem();

        assertEquals("Brand", copiedItem.getBrand());
        assertEquals("Model", copiedItem.getModel());
        assertEquals(12, copiedItem.getWarrantyMonths());
    }

    @Test
    void managedListingUpdateRejectsBlankTitle() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 25, 10, 0);
        Auction auction = newAuction(startTime, startTime.plusMinutes(10), startTime.plusMinutes(1));

        assertThrows(ValidationException.class, () -> auction.updateManagedListingDetails(
                "",
                "Updated description",
                startTime.plusMinutes(12)
        ));
    }

    @Test
    void closeIfEndedDeterminesWinnerFromLeadingBidder() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime endTime = startTime.plusMinutes(10);
        AdjustableClock clock = new AdjustableClock(startTime.plusMinutes(1));
        Auction auction = newAuction(startTime, endTime, clock);
        auction.placeBid(
                new BidTransaction("bid-1", new BigDecimal("110.00"), startTime.plusMinutes(1), "bidder-1", "auction-1")
        );
        clock.setTime(endTime);

        boolean closed = auction.closeIfEnded();

        assertTrue(closed);
        assertEquals(AuctionStatus.PAID, auction.getStatus());
        assertEquals("bidder-1", auction.getWinnerBidderId());
    }


    @Test
    void createAuctionSuccessfully() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDateTime endTime = startTime.plusDays(7);
        Clock clock = Clock.fixed(startTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        Auction auction = new Auction(
                "new-auction-id",
                "new-seller-id",
                startTime,
                endTime,
                new Electronics(
                        "new-item-id",
                        "New Gadget",
                        "A brand new gadget for auction",
                        new BigDecimal("50.00"),
                        "TechBrand",
                        "ModelX",
                        6
                ),
                new BigDecimal("50.00"),
                new BigDecimal("5.00"),
                clock
        );

        assertEquals("new-auction-id", auction.getId());
        assertEquals("new-seller-id", auction.getSellerId());
        assertEquals(startTime, auction.getStartTime());
        assertEquals(endTime, auction.getEndTime());
        assertEquals("New Gadget", auction.getItem().getTitle());
        assertEquals(new BigDecimal("50.00"), auction.getStartingPrice());
        assertEquals(new BigDecimal("5.00"), auction.getMinimumBidIncrement());
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
        assertEquals(new BigDecimal("50.00"), auction.getCurrentPrice());
    }

    @Test
    void updateAuctionDetailsSuccessfully() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 25, 10, 0);
        LocalDateTime originalEndTime = startTime.plusMinutes(10);
        Auction auction = newAuction(startTime, originalEndTime, startTime.minusMinutes(1));
        Auction candidate = auction.snapshotCopy();

        LocalDateTime newEndTime = originalEndTime.plusDays(1);
        candidate.updateManagedListingDetails(
                "Updated laptop title",
                "Updated description for the laptop",
                newEndTime
        );

        auction.applySnapshot(candidate);

        assertEquals("Updated laptop title", auction.getItem().getTitle());
        assertEquals("Updated description for the laptop", auction.getItem().getDescription());
        assertEquals(newEndTime, auction.getEndTime());
    }

    @Test
    void cancelAuctionSuccessfully() {

        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime endTime = startTime.plusMinutes(10);
        AdjustableClock clock = new AdjustableClock(startTime.plusMinutes(1));
        Auction auction = newAuction(startTime, endTime, clock);

        auction.cancel();

        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
        assertFalse(auction.closeIfEnded());
    }



    private Auction newAuction(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        return newAuction(startTime, endTime, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private Auction newAuction(LocalDateTime startTime, LocalDateTime endTime, Clock clock) {
        return new Auction(
                "auction-1",
                "seller-1",
                startTime,
                endTime,
                new Electronics(
                        "item-1",
                        "Laptop",
                        "Test item",
                        new BigDecimal("100.00"),
                        "Brand",
                        "Model",
                        12
                ),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                clock
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
