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
    private final AuctionMutationExecutor auctionMutations;

    public AuctionQuery(
            Map<String, Auction> auctions,
            AuthManager authManager,
            AuctionMutationExecutor auctionMutations
    ) {
        this.auctions = auctions;
        this.authManager = authManager;
        this.auctionMutations = auctionMutations;
    }

    public List<AuctionSummaryDto> getAuctionSummaries() {
        return auctionMutations.executeWithLock(() -> {
            List<AuctionSummaryDto> result = new ArrayList<>();
            for (Auction auction : auctions.values()) {
                result.add(buildAuctionSummaryUnlocked(auction));
            }
            return result;
        });
    }

    public List<AuctionSummaryDto> getAuctionSummaries(Iterable<String> auctionIds) {
        return auctionMutations.executeWithLock(() -> {
            List<AuctionSummaryDto> result = new ArrayList<>();
            if (auctionIds == null) {
                return result;
            }
            for (String auctionId : auctionIds) {
                Auction auction = auctions.get(auctionId);
                if (auction != null) {
                    result.add(buildAuctionSummaryUnlocked(auction));
                }
            }
            return result;
        });
    }

    public boolean hasAuction(String auctionId) {
        return auctionId != null && auctions.containsKey(auctionId);
    }

    public List<ActivitySummaryDto> getActivityForUser(String userId) {
        return auctionMutations.executeWithLock(() -> auctions.values().stream()
                .filter(auction -> auction.getActiveBidHistory().stream()
                        .anyMatch(bid -> userId.equals(StringUtil.normalizeString(bid.getBidderId()))))
                .map(auction -> buildActivitySummary(auction, userId))
                .filter(Objects::nonNull)
                .toList());
    }

    public List<ListingSummaryDto> getListingsForUser(String userId) {
        return auctionMutations.executeWithLock(() -> auctions.values().stream()
                .filter(auction -> userId.equals(StringUtil.normalizeString(auction.getSellerId())))
                .map(this::buildListingSummary)
                .toList());
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
        return auctionMutations.executeWithLock(() -> buildAuctionSummaryUnlocked(auction));
    }

    public AuctionDetailsResponseMessage buildAuctionDetailsResponse(Auction auction) {
        return auctionMutations.executeWithLock(() -> buildAuctionDetailsResponseUnlocked(auction));
    }

    private AuctionSummaryDto buildAuctionSummaryUnlocked(Auction auction) {
        AuctionView view = auctionView(auction);
        return new AuctionSummaryDto(
                view.auction().getId(),
                view.item().getTitle(),
                view.auction().getCurrentPrice(),
                view.auction().getMinimumNextBid(),
                view.auction().getStatus(),
                view.auction().getLeadingBidderId(),
                view.auction().getStartTime(),
                view.auction().getEndTime(),
                view.item().getImageUrl(),
                view.item().getType(),
                view.leadingName(),
                view.auction().getSellerId(),
                view.sellerName()
        );
    }

    private AuctionDetailsResponseMessage buildAuctionDetailsResponseUnlocked(Auction auction) {
        AuctionView view = auctionView(auction);
        List<BidDto> bids = toBidDtos(view.auction().getActiveBidHistory());
        return new AuctionDetailsResponseMessage(
                view.auction().getId(),
                view.auction().getSellerId(),
                view.item().getTitle(),
                view.item().getDescription(),
                view.auction().getStartingPrice(),
                view.auction().getCurrentPrice(),
                view.auction().getMinimumNextBid(),
                view.auction().getStatus(),
                view.auction().getLeadingBidderId(),
                view.auction().getWinnerBidderId(),
                view.auction().getStartTime(),
                view.auction().getEndTime(),
                view.item().getImageUrl(),
                view.item().getType(),
                bids,
                view.leadingName(),
                view.winnerName(),
                view.sellerName()
        );
    }

    private ActivitySummaryDto buildActivitySummary(Auction auction, String userId) {
        AuctionView snapshot = auctionView(auction);
        List<BidTransaction> activeBids = snapshot.auction().getActiveBidHistory();
        List<BidTransaction> userBids = activeBids.stream()
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
                activeBids.size(),
                snapshot.winnerName(),
                snapshot.auction().getEndTime(),
                snapshot.item().getImageUrl(),
                snapshot.item().getType()
        );
    }

    private ListingSummaryDto buildListingSummary(Auction auction) {
        AuctionView snapshot = auctionView(auction);
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

    private String listingBidderValue(AuctionView snapshot, String status) {
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

    private AuctionView auctionView(Auction auction) {
        Item item = auction.getItem();
        return new AuctionView(
                auction,
                item,
                displayUsername(auction.getLeadingBidderId()),
                displayUsername(auction.getWinnerBidderId()),
                displayUsername(auction.getSellerId())
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

    private record AuctionView(
            Auction auction,
            Item item,
            String leadingName,
            String winnerName,
            String sellerName
    ) {
    }
}
