package net.auctionapp.server.models.auction;

import net.auctionapp.server.models.items.Electronics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuctionBidInvalidationTest {
    @Test
    void invalidatingLeadingBidRecalculatesPriceAndLeaderFromActiveBids() {
        Auction auction = newAuction();
        auction.restoreBidHistory(List.of(
                bid("bid-1", "bidder-1", "110.00"),
                bid("bid-2", "bidder-2", "120.00"),
                bid("bid-3", "bidder-1", "130.00")
        ));

        List<BidTransaction> invalidatedBids = auction.invalidateActiveBidsBy("bidder-1");

        assertEquals(2, invalidatedBids.size());
        assertEquals(BidStatus.INVALIDATED, invalidatedBids.getFirst().getStatus());
        assertEquals(new BigDecimal("120.00"), auction.getCurrentPrice());
        assertEquals("bidder-2", auction.getLeadingBidderId());
        assertEquals(1, auction.getActiveBidHistory().size());
        assertEquals(3, auction.getBidHistory().size());
    }

    @Test
    void invalidatingOnlyBidResetsAuctionToStartingPriceWithoutLeader() {
        Auction auction = newAuction();
        auction.restoreBidHistory(List.of(bid("bid-1", "bidder-1", "110.00")));

        auction.invalidateActiveBidsBy("bidder-1");

        assertEquals(new BigDecimal("100.00"), auction.getCurrentPrice());
        assertNull(auction.getLeadingBidderId());
        assertEquals(List.of(), auction.getActiveBidHistory());
    }

    private Auction newAuction() {
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 23, 10, 0);
        return new Auction(
                "auction-1",
                "seller-1",
                startTime,
                startTime.plusHours(1),
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
                new BigDecimal("130.00"),
                "bidder-1",
                null,
                net.auctionapp.common.auction.AuctionStatus.RUNNING
        );
    }

    private BidTransaction bid(String bidId, String bidderId, String amount) {
        return new BidTransaction(
                bidId,
                new BigDecimal(amount),
                LocalDateTime.of(2026, 5, 23, 10, 30),
                bidderId,
                "auction-1"
        );
    }
}
