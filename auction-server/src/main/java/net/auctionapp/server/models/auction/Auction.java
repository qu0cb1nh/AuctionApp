package net.auctionapp.server.models.auction;

import net.auctionapp.common.auction.AuctionStatus;

import net.auctionapp.server.models.Entity;
import net.auctionapp.server.models.items.Item;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregate root for an auction session.
 */
public class Auction extends Entity {
    private static final long ANTI_SNIPING_THRESHOLD_SECONDS = 10;
    private static final long ANTI_SNIPING_EXTENSION_SECONDS = 10;

    private final String sellerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private final Item item;
    private BigDecimal startingPrice;
    private BigDecimal minimumBidIncrement;
    private BigDecimal currentPrice;
    private String leadingBidderId;
    private String winnerBidderId;
    private AuctionStatus status;

    private final List<BidTransaction> bidHistory = new ArrayList<>();

    public Auction(
            String id,
            String sellerId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Item item,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement
    ) {
        super(id);
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.item = item;
        this.startingPrice = startingPrice;
        this.minimumBidIncrement = minimumBidIncrement;
        this.currentPrice = startingPrice;
        this.status = AuctionStatus.RUNNING;
    }

    public Auction(
            String id,
            String sellerId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Item item,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            BigDecimal currentPrice,
            String leadingBidderId,
            String winnerBidderId,
            AuctionStatus status
    ) {
        this(id, sellerId, startTime, endTime, item, startingPrice, minimumBidIncrement);
        this.currentPrice = currentPrice == null ? startingPrice : currentPrice;
        this.leadingBidderId = leadingBidderId;
        this.winnerBidderId = winnerBidderId;
        this.status = status == null ? AuctionStatus.RUNNING : status;
    }

    public synchronized String getSellerId() {
        return sellerId;
    }

    public synchronized LocalDateTime getStartTime() {
        return startTime;
    }

    public synchronized LocalDateTime getEndTime() {
        return endTime;
    }

    public synchronized Item getItem() {
        return item.copy();
    }

    public synchronized BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public synchronized BigDecimal getMinimumBidIncrement() {
        return minimumBidIncrement;
    }

    public synchronized BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public synchronized String getLeadingBidderId() {
        return leadingBidderId;
    }

    public synchronized String getWinnerBidderId() {
        return winnerBidderId;
    }

    public synchronized AuctionStatus getStatus() {
        return status;
    }

    public synchronized List<BidTransaction> getBidHistory() {
        return new ArrayList<>(bidHistory);
    }

    public synchronized void restoreBidHistory(List<BidTransaction> persistedBids) {
        bidHistory.clear();
        if (persistedBids == null || persistedBids.isEmpty()) {
            restoreLeadingBidFromAuctionState();
            return;
        }
        persistedBids.stream()
                .filter(bid -> bid != null && getId().equals(bid.getAuctionId()))
                .filter(bid -> bid.getAmount() != null && bid.getBidderId() != null && !bid.getBidderId().isBlank())
                .sorted(Comparator.comparing(BidTransaction::getTimestamp, Comparator.nullsLast(LocalDateTime::compareTo)))
                .forEach(bidHistory::add);
        if (bidHistory.isEmpty()) {
            restoreLeadingBidFromAuctionState();
        }
    }

    public synchronized boolean placeBid(BidTransaction bid, LocalDateTime now) {
        if (status != AuctionStatus.RUNNING) {
            return false;
        }
        if (now == null || now.isBefore(startTime) || !now.isBefore(endTime)) {
            return false;
        }
        if (bid == null || bid.getAmount() == null) {
            return false;
        }
        if (!getId().equals(bid.getAuctionId())) {
            return false;
        }
        if (sellerId != null && sellerId.equals(bid.getBidderId())) {
            return false;
        }

        BigDecimal minimumNextBid = getMinimumNextBid();
        if (minimumNextBid != null && bid.getAmount().compareTo(minimumNextBid) < 0) {
            return false;
        }

        currentPrice = bid.getAmount();
        leadingBidderId = bid.getBidderId();
        bidHistory.add(bid);

        long secondsLeft = Duration.between(now, endTime).getSeconds();
        if (secondsLeft <= ANTI_SNIPING_THRESHOLD_SECONDS) {
            endTime = endTime.plusSeconds(ANTI_SNIPING_EXTENSION_SECONDS);
        }

        return true;
    }

    public synchronized boolean updateListingDetails(
            String title,
            String description,
            BigDecimal newStartingPrice,
            BigDecimal newMinimumBidIncrement,
            LocalDateTime newStartTime,
            LocalDateTime newEndTime,
            LocalDateTime now
    ) {
        if (title == null || title.isBlank()
                || description == null || description.isBlank()
                || newStartingPrice == null || newStartingPrice.signum() < 0
                || newMinimumBidIncrement == null || newMinimumBidIncrement.signum() <= 0
                || newStartTime == null || newEndTime == null
                || !newEndTime.isAfter(newStartTime)) {
            return false;
        }

        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        boolean biddingHasStarted = !effectiveNow.isBefore(startTime) || !bidHistory.isEmpty();
        if (biddingHasStarted && (newStartingPrice.compareTo(startingPrice) != 0
                || !newStartTime.equals(startTime)
                || !newEndTime.isAfter(effectiveNow))) {
            return false;
        }

        item.updateDetails(title, description, newStartingPrice);
        startingPrice = newStartingPrice;
        minimumBidIncrement = newMinimumBidIncrement;
        startTime = newStartTime;
        endTime = newEndTime;
        if (!biddingHasStarted) {
            currentPrice = newStartingPrice;
            leadingBidderId = null;
            winnerBidderId = null;
            bidHistory.clear();
        }
        return true;
    }

    public synchronized void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public synchronized void setWinnerBidderId(String winnerBidderId) {
        this.winnerBidderId = winnerBidderId;
    }

    public synchronized BigDecimal getMinimumNextBid() {
        if (currentPrice == null || minimumBidIncrement == null) {
            return null;
        }
        return currentPrice.add(minimumBidIncrement);
    }

    public synchronized Auction snapshotCopy() {
        Auction copy = new Auction(
                getId(),
                sellerId,
                startTime,
                endTime,
                item.copy(),
                startingPrice,
                minimumBidIncrement,
                currentPrice,
                leadingBidderId,
                winnerBidderId,
                status
        );
        copy.bidHistory.addAll(bidHistory);
        return copy;
    }

    public synchronized void applySnapshot(Auction snapshot) {
        if (snapshot == null || !getId().equals(snapshot.getId())) {
            throw new IllegalArgumentException("Snapshot must belong to this auction.");
        }
        Item snapshotItem = snapshot.getItem();
        item.updateDetails(snapshotItem.getTitle(), snapshotItem.getDescription(), snapshotItem.getBasePrice());
        item.setImageUrl(snapshotItem.getImageUrl());
        startTime = snapshot.getStartTime();
        endTime = snapshot.getEndTime();
        startingPrice = snapshot.getStartingPrice();
        minimumBidIncrement = snapshot.getMinimumBidIncrement();
        currentPrice = snapshot.getCurrentPrice();
        leadingBidderId = snapshot.getLeadingBidderId();
        winnerBidderId = snapshot.getWinnerBidderId();
        status = snapshot.getStatus();
        bidHistory.clear();
        bidHistory.addAll(snapshot.getBidHistory());
    }

    private void restoreLeadingBidFromAuctionState() {
        if (leadingBidderId == null || leadingBidderId.isBlank() || currentPrice == null) {
            return;
        }
        if (startingPrice != null && currentPrice.compareTo(startingPrice) <= 0) {
            return;
        }
        bidHistory.add(new BidTransaction(
                "restored-" + getId(),
                currentPrice,
                startTime,
                leadingBidderId,
                getId()
        ));
    }

}
