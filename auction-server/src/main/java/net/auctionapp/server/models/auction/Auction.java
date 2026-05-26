package net.auctionapp.server.models.auction;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.utils.MoneyUtil;
import net.auctionapp.common.utils.StringUtil;

import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.models.Entity;
import net.auctionapp.server.models.items.Item;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root for an auction session.
 */
public class Auction extends Entity {
    private static final long ANTI_SNIPING_THRESHOLD_SECONDS = 30;
    private static final long ANTI_SNIPING_EXTENSION_SECONDS = 60;

    private final String sellerId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private final Item item;
    private final BigDecimal startingPrice;
    private final BigDecimal minimumBidIncrement;
    private BigDecimal currentPrice;
    private String leadingBidderId;
    private String winnerBidderId;
    private AuctionStatus status;
    private final Clock clock;

    private final List<BidTransaction> bidHistory = new ArrayList<>();

    public Auction(
            String id,
            String sellerId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Item item,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            Clock clock
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
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
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
        this(
                id,
                sellerId,
                startTime,
                endTime,
                item,
                startingPrice,
                minimumBidIncrement,
                currentPrice,
                leadingBidderId,
                winnerBidderId,
                status,
                Clock.systemDefaultZone()
        );
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
            AuctionStatus status,
            Clock clock
    ) {
        this(id, sellerId, startTime, endTime, item, startingPrice, minimumBidIncrement, clock);
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

    public synchronized List<BidTransaction> getActiveBidHistory() {
        return bidHistory.stream()
                .filter(BidTransaction::isActive)
                .toList();
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

    public void placeBid(BidTransaction bid) {
        LocalDateTime now = LocalDateTime.now(clock);
        requireOpenForBidding(now);
        if (bid == null || !getId().equals(bid.getAuctionId())) {
            throw new ValidationException("Bid does not belong to this auction.");
        }
        MoneyUtil.requirePositiveMoney(bid.getAmount(), "Bid amount");
        if (bid.getBidderId() == null || bid.getBidderId().isBlank()) {
            throw new ValidationException("Bidder is required.");
        }
        if (sellerId.equals(bid.getBidderId())) {
            throw new ValidationException("Seller cannot bid on own auction.");
        }
        BigDecimal minimumNextBid = getMinimumNextBid();
        if (bid.getAmount().compareTo(minimumNextBid) < 0) {
            throw new ValidationException("Bid must be at least " + minimumNextBid + ".");
        }

        currentPrice = bid.getAmount();
        leadingBidderId = bid.getBidderId();
        bidHistory.add(bid);

        long secondsLeft = Duration.between(now, endTime).getSeconds();
        if (secondsLeft <= ANTI_SNIPING_THRESHOLD_SECONDS) {
            endTime = endTime.plusSeconds(ANTI_SNIPING_EXTENSION_SECONDS);
        }
    }

    public void updateManagedListingDetails(
            String title,
            String description,
            LocalDateTime newEndTime
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        requireOpenForManagement(now);
        if (title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new ValidationException("Auction update data is invalid.");
        }
        if (newEndTime == null || !newEndTime.isAfter(now)) {
            throw new ValidationException("End time must be after the current time.");
        }
        if (newEndTime.isBefore(endTime)) {
            throw new ValidationException("End time cannot be earlier than the current end time.");
        }
        item.updateDetails(title, description, startingPrice);
        endTime = newEndTime;
    }

    public void closeManually() {
        LocalDateTime now = LocalDateTime.now(clock);
        requireOpenForManagement(now);
        finish(false);
    }

    public void cancel() {
        LocalDateTime now = LocalDateTime.now(clock);
        requireOpenForManagement(now);
        finish(true);
    }

    public boolean closeIfEnded() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (status != AuctionStatus.RUNNING
                || now.isBefore(endTime)) {
            return false;
        }
        finish(false);
        return true;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public void setWinnerBidderId(String winnerBidderId) {
        this.winnerBidderId = winnerBidderId;
    }

    public List<BidTransaction> invalidateActiveBidsBy(String bidderId) {
        List<BidTransaction> invalidatedBids = new ArrayList<>();
        if (bidderId == null || bidderId.isBlank()) {
            return invalidatedBids;
        }
        for (int index = 0; index < bidHistory.size(); index++) {
            BidTransaction bid = bidHistory.get(index);
            if (bid != null && bid.isActive() && bidderId.equalsIgnoreCase(bid.getBidderId())) {
                BidTransaction invalidatedBid = bid.invalidate();
                bidHistory.set(index, invalidatedBid);
                invalidatedBids.add(invalidatedBid);
            }
        }
        if (!invalidatedBids.isEmpty()) {
            recalculateLeadingBid();
        }
        return invalidatedBids;
    }

    public synchronized BigDecimal getMinimumNextBid() {
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
                status,
                clock
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
        endTime = snapshot.getEndTime();
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

    private void recalculateLeadingBid() {
        BidTransaction highestActiveBid = bidHistory.stream()
                .filter(bid -> bid != null && bid.isActive() && bid.getAmount() != null)
                .max(Comparator.comparing(BidTransaction::getAmount))
                .orElse(null);
        if (highestActiveBid == null) {
            currentPrice = startingPrice;
            leadingBidderId = null;
            return;
        }
        currentPrice = highestActiveBid.getAmount();
        leadingBidderId = highestActiveBid.getBidderId();
    }

    private void requireOpenForBidding(LocalDateTime now) {
        if (status != AuctionStatus.RUNNING
                || now.isBefore(startTime)
                || !now.isBefore(endTime)) {
            throw new InvalidAuctionStateException("Auction is not open for bidding.");
        }
    }

    private void requireOpenForManagement(LocalDateTime now) {
        if (status != AuctionStatus.RUNNING
                || !now.isBefore(endTime)) {
            throw new InvalidAuctionStateException("Only running auctions can be managed.");
        }
    }

    private void finish(boolean forceCancel) {
        String normalizedLeadingBidderId = StringUtil.normalizeString(leadingBidderId);
        boolean hasWinner = !forceCancel && !normalizedLeadingBidderId.isEmpty();
        winnerBidderId = hasWinner ? normalizedLeadingBidderId : null;
        status = hasWinner ? AuctionStatus.PAID : AuctionStatus.CANCELED;
    }

}
