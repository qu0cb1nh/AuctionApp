package net.auctionapp.server.managers.auction;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.dto.ActivitySummaryDto;
import net.auctionapp.common.dto.ListingSummaryDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.auction.MyActivityResponseMessage;
import net.auctionapp.common.messages.auction.MyListingsResponseMessage;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.items.Electronics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuctionQueryTest {
    @Test
    void activitySummaryCalculatesUserBidValuesWithoutReturningHistory() {
        Auction auction = auction(
                "activity-1",
                AuctionStatus.RUNNING,
                "bidder-1",
                null,
                LocalDateTime.of(2099, 5, 27, 12, 0)
        );
        auction.restoreBidHistory(List.of(
                bid("bid-1", "bidder-2", "120.00", "activity-1"),
                bid("bid-2", "bidder-1", "130.00", "activity-1")
        ));

        ActivitySummaryDto result = query(auction).getActivityForUser("bidder-1").getFirst();

        assertEquals("Active", result.getStatus());
        assertEquals("Leading", result.getBidPosition());
        assertEquals(new BigDecimal("130.00"), result.getYourMaxBid());
        assertEquals(2, result.getBidCount());
    }

    @Test
    void listingSummaryReturnsDisplayDataWithoutDetailFields() {
        Auction auction = auction(
                "listing-1",
                AuctionStatus.PAID,
                "bidder-1",
                "bidder-1",
                LocalDateTime.of(2020, 5, 27, 12, 0)
        );

        ListingSummaryDto result = query(auction).getListingsForUser("seller-1").getFirst();

        assertEquals("Sold", result.getStatus());
        assertEquals("Winner", result.getBidderCaption());
        assertEquals("bidder-1", result.getBidderValue());
        assertEquals(new BigDecimal("130.00"), result.getCurrentPrice());
    }

    @Test
    void compactListResponsesRoundTripThroughJson() {
        Auction auction = auction(
                "listing-1",
                AuctionStatus.PAID,
                "bidder-1",
                "bidder-1",
                LocalDateTime.of(2020, 5, 27, 12, 0)
        );
        ListingSummaryDto listing = query(auction).getListingsForUser("seller-1").getFirst();

        Message listingMessage = JsonUtil.fromJson(JsonUtil.toJson(new MyListingsResponseMessage(List.of(listing))));
        Message activityMessage = JsonUtil.fromJson(JsonUtil.toJson(new MyActivityResponseMessage(List.of())));

        assertEquals(1, assertInstanceOf(MyListingsResponseMessage.class, listingMessage).getListings().size());
        assertEquals(0, assertInstanceOf(MyActivityResponseMessage.class, activityMessage).getActivities().size());
    }

    private AuctionQuery query(Auction auction) {
        return new AuctionQuery(Map.of(auction.getId(), auction), AuthManager.getInstance());
    }

    private Auction auction(
            String auctionId,
            AuctionStatus status,
            String leadingBidderId,
            String winnerBidderId,
            LocalDateTime endTime
    ) {
        return new Auction(
                auctionId,
                "seller-1",
                LocalDateTime.of(2020, 5, 27, 10, 0),
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
                new BigDecimal("130.00"),
                leadingBidderId,
                winnerBidderId,
                status
        );
    }

    private BidTransaction bid(String bidId, String bidderId, String amount, String auctionId) {
        return new BidTransaction(
                bidId,
                new BigDecimal(amount),
                LocalDateTime.of(2026, 5, 27, 10, 30),
                bidderId,
                auctionId
        );
    }
}
