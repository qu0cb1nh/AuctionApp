package net.auctionapp.server.managers.auction;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.dto.ActivitySummaryDto;
import net.auctionapp.common.dto.AuctionSummaryDto;
import net.auctionapp.common.dto.BidDto;
import net.auctionapp.common.dto.ListingSummaryDto;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.managers.AuthManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AuctionQuery {
    private static final String ACTIVITY_ACTIVE = "Active";
    private static final String ACTIVITY_WON = "Won";
    private static final String ACTIVITY_LOST = "Lost";
    private static final String POSITION_LEADING = "Leading";
    private static final String POSITION_OUTBID = "Outbid";
    private static final String POSITION_CLOSED = "Closed";
    private static final String LISTING_ACTIVE = "Active";
    private static final String LISTING_SOLD = "Sold";
    private static final String LISTING_CANCELED = "Canceled";

    private final Map<String, Auction> auctions;
    private final AuthManager authManager;

    public AuctionQuery(Map<String, Auction> auctions, AuthManager authManager) {
        this.auctions = auctions;
        this.authManager = authManager;
    }

    public List<AuctionSummaryDto> getAuctionSummaries() {
        List<AuctionSummaryDto> result = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            result.add(buildAuctionSummary(auction));
        }
        return result;
    }

    public List<AuctionSummaryDto> getAuctionSummaries(Iterable<String> auctionIds) {
        List<AuctionSummaryDto> result = new ArrayList<>();
        if (auctionIds == null) {
            return result;
        }
        for (String auctionId : auctionIds) {
            Auction auction = auctions.get(auctionId);
            if (auction != null) {
                result.add(buildAuctionSummary(auction));
            }
        }
        return result;
    }

    public boolean hasAuction(String auctionId) {
        return auctionId != null && auctions.containsKey(auctionId);
    }

    public List<ActivitySummaryDto> getActivityForUser(String userId) {
        return auctions.values().stream()
                .filter(auction -> auction.getActiveBidHistory().stream()
                        .anyMatch(bid -> userId.equals(StringUtil.normalizeString(bid.getBidderId()))))
                .map(auction -> buildActivitySummary(auction, userId))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ListingSummaryDto> getListingsForUser(String userId) {
        return auctions.values().stream()
                .filter(auction -> userId.equals(StringUtil.normalizeString(auction.getSellerId())))
                .map(this::buildListingSummary)
                .toList();
    }

    public Auction requireAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            throw new NotFoundException("Auction not found.");
        }
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new NotFoundException("Auction not found.");
        }
        return auction;
    }

    public AuctionSummaryDto buildAuctionSummary(Auction auction) {
        AuctionSnapshot snapshot = snapshotView(auction);
        return new AuctionSummaryDto(
                snapshot.auction().getId(),
                snapshot.item().getTitle(),
                snapshot.auction().getCurrentPrice(),
                snapshot.auction().getMinimumNextBid(),
                snapshot.auction().getStatus(),
                snapshot.auction().getLeadingBidderId(),
                snapshot.auction().getStartTime(),
                snapshot.auction().getEndTime(),
                snapshot.item().getImageUrl(),
                snapshot.item().getType(),
                snapshot.leadingName(),
                snapshot.auction().getSellerId(),
                snapshot.sellerName()
        );
    }

    public AuctionDetailsResponseMessage buildAuctionDetailsResponse(Auction auction) {
        AuctionSnapshot snapshot = snapshotView(auction);
        List<BidDto> bids = toBidDtos(snapshot.auction().getActiveBidHistory());
        return new AuctionDetailsResponseMessage(
                snapshot.auction().getId(),
                snapshot.auction().getSellerId(),
                snapshot.item().getTitle(),
                snapshot.item().getDescription(),
                snapshot.auction().getStartingPrice(),
                snapshot.auction().getCurrentPrice(),
                snapshot.auction().getMinimumNextBid(),
                snapshot.auction().getStatus(),
                snapshot.auction().getLeadingBidderId(),
                snapshot.auction().getWinnerBidderId(),
                snapshot.auction().getStartTime(),
                snapshot.auction().getEndTime(),
                snapshot.item().getImageUrl(),
                snapshot.item().getType(),
                bids,
                snapshot.leadingName(),
                snapshot.winnerName(),
                snapshot.sellerName()
        );
    }

    private ActivitySummaryDto buildActivitySummary(Auction auction, String userId) {
        AuctionSnapshot snapshot = snapshotView(auction);
        List<BidTransaction> bids = snapshot.auction().getActiveBidHistory();
        List<BidTransaction> userBids = bids.stream()
                .filter(bid -> userId.equals(StringUtil.normalizeString(bid.getBidderId())))
                .filter(bid -> bid.getAmount() != null)
                .toList();
        if (userBids.isEmpty()) {
            return null;
        }
        BigDecimal yourMaxBid = userBids.stream()
                .map(BidTransaction::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return new ActivitySummaryDto(
                snapshot.auction().getId(),
                snapshot.item().getTitle(),
                activityStatus(snapshot.auction(), userId),
                bidPosition(snapshot.auction(), userId),
                snapshot.auction().getSellerId(),
                snapshot.sellerName(),
                yourMaxBid,
                snapshot.auction().getCurrentPrice(),
                bids.size(),
                snapshot.winnerName(),
                snapshot.auction().getEndTime(),
                snapshot.item().getImageUrl(),
                snapshot.item().getType()
        );
    }

    private ListingSummaryDto buildListingSummary(Auction auction) {
        AuctionSnapshot snapshot = snapshotView(auction);
        String status = listingStatus(snapshot.auction());
        return new ListingSummaryDto(
                snapshot.auction().getId(),
                snapshot.item().getTitle(),
                status,
                snapshot.sellerName(),
                snapshot.auction().getCurrentPrice(),
                snapshot.auction().getEndTime(),
                listingBidderCaption(snapshot.auction(), status),
                listingBidderValue(snapshot, status),
                snapshot.item().getImageUrl(),
                snapshot.item().getType()
        );
    }

    private String activityStatus(Auction auction, String userId) {
        if (isRunning(auction)) {
            return ACTIVITY_ACTIVE;
        }
        if (userId.equals(StringUtil.normalizeString(auction.getWinnerBidderId()))
                || isEndedWhileRunning(auction)
                && userId.equals(StringUtil.normalizeString(auction.getLeadingBidderId()))) {
            return ACTIVITY_WON;
        }
        return ACTIVITY_LOST;
    }

    private String bidPosition(Auction auction, String userId) {
        if (!isRunning(auction)) {
            return POSITION_CLOSED;
        }
        return userId.equals(StringUtil.normalizeString(auction.getLeadingBidderId()))
                ? POSITION_LEADING
                : POSITION_OUTBID;
    }

    private String listingStatus(Auction auction) {
        if (auction.getStatus() == AuctionStatus.CANCELED) {
            return LISTING_CANCELED;
        }
        if (isRunning(auction)) {
            return LISTING_ACTIVE;
        }
        if (hasBidder(auction.getWinnerBidderId())
                || isEndedWhileRunning(auction) && !auction.getActiveBidHistory().isEmpty()) {
            return LISTING_SOLD;
        }
        return LISTING_CANCELED;
    }

    private String listingBidderCaption(Auction auction, String status) {
        return auction.getStatus() == AuctionStatus.PAID || LISTING_CANCELED.equals(status)
                ? "Winner"
                : "Top Bidder";
    }

    private String listingBidderValue(AuctionSnapshot snapshot, String status) {
        if (LISTING_CANCELED.equals(status)) {
            return "No winner";
        }
        if (snapshot.auction().getStatus() == AuctionStatus.PAID) {
            return textOrFallback(snapshot.winnerName(), "No winner");
        }
        return textOrFallback(snapshot.leadingName(), "No bids yet");
    }

    private boolean isRunning(Auction auction) {
        return auction.getStatus() == AuctionStatus.RUNNING
                && auction.getEndTime() != null
                && LocalDateTime.now().isBefore(auction.getEndTime());
    }

    private boolean isEndedWhileRunning(Auction auction) {
        return auction.getStatus() == AuctionStatus.RUNNING
                && auction.getEndTime() != null
                && !LocalDateTime.now().isBefore(auction.getEndTime());
    }

    private boolean hasBidder(String bidderId) {
        return bidderId != null && !bidderId.isBlank();
    }

    private String textOrFallback(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    public String displayUsername(String userId) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        if (normalizedUserId.isEmpty()) {
            return null;
        }
        try {
            return authManager.requireUserById(normalizedUserId).getUsername();
        } catch (NotFoundException | DatabaseException e) {
            return normalizedUserId;
        }
    }

    private AuctionSnapshot snapshotView(Auction auction) {
        Auction snapshot = auction.snapshotCopy();
        Item item = snapshot.getItem();
        return new AuctionSnapshot(
                snapshot,
                item,
                displayUsername(snapshot.getLeadingBidderId()),
                displayUsername(snapshot.getWinnerBidderId()),
                displayUsername(snapshot.getSellerId())
        );
    }

    private List<BidDto> toBidDtos(List<BidTransaction> bids) {
        if (bids == null || bids.isEmpty()) {
            return List.of();
        }
        List<BidDto> result = new ArrayList<>(bids.size());
        for (BidTransaction bid : bids) {
            result.add(new BidDto(bid.getId(), bid.getBidderId(), bid.getAmount(), bid.getTimestamp()));
        }
        return result;
    }

    private record AuctionSnapshot(
            Auction auction,
            Item item,
            String leadingName,
            String winnerName,
            String sellerName
    ) {
    }
}
