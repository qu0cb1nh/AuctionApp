package net.auctionapp.server.models.auction;

import net.auctionapp.server.models.items.Electronics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionTest {
    @Test
    void placeBidExtendsEndTimeWhenBidArrivesInsideAntiSnipeWindow() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime endTime = startTime.plusMinutes(1);
        Auction auction = newAuction(startTime, endTime);

        boolean accepted = auction.placeBid(
                new BidTransaction("bid-1", new BigDecimal("110.00"), endTime.minusSeconds(5), "bidder-1", "auction-1"),
                endTime.minusSeconds(5)
        );

        assertTrue(accepted);
        assertEquals(endTime.plusSeconds(10), auction.getEndTime());
    }

    @Test
    void placeBidKeepsEndTimeWhenBidArrivesOutsideAntiSnipeWindow() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime endTime = startTime.plusMinutes(1);
        Auction auction = newAuction(startTime, endTime);

        boolean accepted = auction.placeBid(
                new BidTransaction("bid-1", new BigDecimal("110.00"), endTime.minusSeconds(11), "bidder-1", "auction-1"),
                endTime.minusSeconds(11)
        );

        assertTrue(accepted);
        assertEquals(endTime, auction.getEndTime());
    }

    private Auction newAuction(LocalDateTime startTime, LocalDateTime endTime) {
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
                new BigDecimal("10.00")
        );
    }
}
