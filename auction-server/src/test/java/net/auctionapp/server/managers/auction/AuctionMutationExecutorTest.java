package net.auctionapp.server.managers.auction;

import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.items.Electronics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionMutationExecutorTest {
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 6, 1, 10, 0);

    @Test
    void failedPersistenceLeavesOriginalAuctionUnchanged() {
        Auction auction = newAuction();
        AuctionMutationExecutor executor = new AuctionMutationExecutor();

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                executor.executeWithLock(
                        auction,
                        candidate -> {
                            candidate.placeBid(bid("bid-1", "bidder-1", "110.00"));
                            return candidate.getCurrentPrice();
                        },
                        (candidate, result) -> {
                            assertEquals(new BigDecimal("110.00"), result);
                            throw new RuntimeException("Persistence failed.");
                        },
                        null
                ));

        assertEquals("Persistence failed.", failure.getMessage());
        assertEquals(new BigDecimal("100.00"), auction.getCurrentPrice());
        assertNull(auction.getLeadingBidderId());
        assertTrue(auction.getBidHistory().isEmpty());
    }

    @Test
    void concurrentMutationsAreSerializedAndUseLatestCommittedPrice() throws Exception {
        Auction auction = newAuction();
        AuctionMutationExecutor executor = new AuctionMutationExecutor();
        CountDownLatch firstMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstMutation = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstBid = workers.submit(() -> executor.executeWithLock(
                    auction,
                    candidate -> {
                        candidate.placeBid(bid("bid-1", "bidder-1", "110.00"));
                        firstMutationStarted.countDown();
                        await(releaseFirstMutation);
                    },
                    candidate -> assertEquals(new BigDecimal("110.00"), candidate.getCurrentPrice())
            ));

            assertTrue(firstMutationStarted.await(2, TimeUnit.SECONDS));

            Future<?> secondBid = workers.submit(() -> executor.executeWithLock(
                    auction,
                    candidate -> candidate.placeBid(bid("bid-2", "bidder-2", "120.00")),
                    candidate -> assertEquals(new BigDecimal("120.00"), candidate.getCurrentPrice())
            ));

            releaseFirstMutation.countDown();
            firstBid.get(2, TimeUnit.SECONDS);
            secondBid.get(2, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }

        assertEquals(new BigDecimal("120.00"), auction.getCurrentPrice());
        assertEquals("bidder-2", auction.getLeadingBidderId());
        assertEquals(2, auction.getActiveBidHistory().size());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch.", e);
        }
    }

    private Auction newAuction() {
        Clock clock = Clock.fixed(START_TIME.plusMinutes(5).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new Auction(
                "auction-mutation-test",
                "seller-1",
                START_TIME,
                START_TIME.plusHours(1),
                new Electronics(
                        "item-mutation-test",
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

    private BidTransaction bid(String bidId, String bidderId, String amount) {
        return new BidTransaction(
                bidId,
                new BigDecimal(amount),
                START_TIME.plusMinutes(5),
                bidderId,
                "auction-mutation-test"
        );
    }
}
