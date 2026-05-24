package net.auctionapp.server.services;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.messages.types.AuctionSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchListServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 24, 16, 0);

    @Test
    void sendsReminderAtFiveMinuteBoundary() {
        assertTrue(WatchListService.isEndingSoonReminderDue(
                auctionEndingAt(NOW.plusMinutes(5), AuctionStatus.RUNNING),
                NOW
        ));
    }

    @Test
    void doesNotSendReminderBeforeFiveMinuteWindow() {
        assertFalse(WatchListService.isEndingSoonReminderDue(
                auctionEndingAt(NOW.plusMinutes(5).plusSeconds(1), AuctionStatus.RUNNING),
                NOW
        ));
    }

    @Test
    void doesNotSendReminderForEndedOrCanceledAuction() {
        assertFalse(WatchListService.isEndingSoonReminderDue(
                auctionEndingAt(NOW.minusSeconds(1), AuctionStatus.RUNNING),
                NOW
        ));
        assertFalse(WatchListService.isEndingSoonReminderDue(
                auctionEndingAt(NOW.plusMinutes(2), AuctionStatus.CANCELED),
                NOW
        ));
    }

    private AuctionSummary auctionEndingAt(LocalDateTime endTime, AuctionStatus status) {
        return new AuctionSummary(
                "auction-1",
                "Laptop",
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                status,
                null,
                NOW.minusMinutes(10),
                endTime
        );
    }
}
