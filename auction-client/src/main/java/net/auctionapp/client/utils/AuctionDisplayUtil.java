package net.auctionapp.client.utils;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.dto.AuctionSummaryDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AuctionDisplayUtil {
    private static final DateTimeFormatter CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AuctionDisplayUtil() {
    }

    public static String formatPrice(BigDecimal value) {
        return value == null ? "N/A" : "$" + value.stripTrailingZeros().toPlainString();
    }

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? "N/A" : CARD_TIME_FORMATTER.format(value);
    }

    public static String formatOwner(String owner) {
        return owner == null || owner.isBlank() ? "Unknown" : owner;
    }

    public static String formatBidder(String bidder) {
        return bidder == null || bidder.isBlank() ? "No bids yet" : bidder;
    }

    public static String displayUsername(String username, String userId) {
        return username == null || username.isBlank() ? userId : username;
    }

    public static String displayStatus(AuctionSummaryDto auction) {
        if (auction == null || auction.getStatus() == null) {
            return "N/A";
        }
        if (auction.getStatus() == AuctionStatus.CANCELED) {
            return "CANCELED";
        }
        if (auction.getStatus() == AuctionStatus.PAID) {
            return "PAID";
        }
        if (auction.getEndTime() != null && !LocalDateTime.now().isBefore(auction.getEndTime())) {
            return auction.getLeadingBidderId() == null || auction.getLeadingBidderId().isBlank()
                    ? "CANCELED"
                    : "PAID";
        }
        return "RUNNING";
    }

    public static String displayStatus(AuctionDetailsResponseMessage auction) {
        if (auction == null || auction.getStatus() == null) {
            return "N/A";
        }
        if (auction.getStatus() == AuctionStatus.CANCELED) {
            return "CANCELED";
        }
        if (auction.getStatus() == AuctionStatus.PAID) {
            return "PAID";
        }
        if (isClosed(auction)) {
            return hasBidder(auction.getWinnerBidderId()) || hasBidder(auction.getLeadingBidderId())
                    ? "PAID"
                    : "CANCELED";
        }
        return "RUNNING";
    }

    public static boolean isRunning(AuctionDetailsResponseMessage auction) {
        return auction != null
                && auction.getStatus() == AuctionStatus.RUNNING
                && auction.getEndTime() != null
                && LocalDateTime.now().isBefore(auction.getEndTime());
    }

    public static boolean isClosed(AuctionDetailsResponseMessage auction) {
        return auction != null
                && auction.getStatus() != null
                && (auction.getStatus() != AuctionStatus.RUNNING
                || auction.getEndTime() != null && !LocalDateTime.now().isBefore(auction.getEndTime()));
    }

    public static String watchListButtonText(boolean watched) {
        return watched ? "Watching" : "Add to watchlist";
    }

    public static String watchListActionMessage(boolean watched) {
        return watched ? "Auction added to your watchlist." : "Auction removed from your watchlist.";
    }

    private static boolean hasBidder(String bidderId) {
        return bidderId != null && !bidderId.isBlank();
    }
}
