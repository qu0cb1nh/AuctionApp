package net.auctionapp.server.models.auction;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.models.items.Electronics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}


