package net.auctionapp.server.managers;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.dto.AuctionSummaryDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchListManagerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 24, 16, 0);

    @Test
    void sendsReminderAtFiveMinuteBoundary() {
        assertTrue(WatchListManager.isEndingSoonReminderDue(
                auctionEndingAt(NOW.plusMinutes(5), AuctionStatus.RUNNING),
                NOW
        ));
    }

    @Test
    void doesNotSendReminderBeforeFiveMinuteWindow() {
        assertFalse(WatchListManager.isEndingSoonReminderDue(
                auctionEndingAt(NOW.plusMinutes(5).plusSeconds(1), AuctionStatus.RUNNING),
                NOW
        ));
    }

    @Test
    void doesNotSendReminderForEndedOrCanceledAuction() {
        assertFalse(WatchListManager.isEndingSoonReminderDue(
                auctionEndingAt(NOW.minusSeconds(1), AuctionStatus.RUNNING),
                NOW
        ));
        assertFalse(WatchListManager.isEndingSoonReminderDue(
                auctionEndingAt(NOW.plusMinutes(2), AuctionStatus.CANCELED),
                NOW
        ));
    }

    private AuctionSummaryDto auctionEndingAt(LocalDateTime endTime, AuctionStatus status) {
        return new AuctionSummaryDto(
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
