package net.auctionapp.common.models.auction;

import net.auctionapp.common.models.Entity;
import net.auctionapp.common.models.items.Art;
import net.auctionapp.common.models.items.Electronics;
import net.auctionapp.common.models.items.Item;
import net.auctionapp.common.models.items.Vehicle;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregate root for an auction session.
 */
public class Auction extends Entity {
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
    private final Map<String, AutoBidConfig> autoBidRegistry = new ConcurrentHashMap<>();

    private long antiSnipingThresholdSeconds = 10;
    private long antiSnipingExtensionSeconds = 10;

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
        this.status = AuctionStatus.OPEN;
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
        this.status = status == null ? AuctionStatus.OPEN : status;
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
        return copyItem(item);
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

    public synchronized Map<String, AutoBidConfig> getAutoBidRegistry() {
        return Map.copyOf(autoBidRegistry);
    }

    public synchronized long getAntiSnipingThresholdSeconds() {
        return antiSnipingThresholdSeconds;
    }

    public synchronized void setAntiSnipingThresholdSeconds(long antiSnipingThresholdSeconds) {
        this.antiSnipingThresholdSeconds = antiSnipingThresholdSeconds;
    }

    public synchronized long getAntiSnipingExtensionSeconds() {
        return antiSnipingExtensionSeconds;
    }

    public synchronized void setAntiSnipingExtensionSeconds(long antiSnipingExtensionSeconds) {
        this.antiSnipingExtensionSeconds = antiSnipingExtensionSeconds;
    }

    public synchronized void refreshStatus(LocalDateTime now) {
        if (status == AuctionStatus.CANCELED || status == AuctionStatus.PAID || status == AuctionStatus.FINISHED) {
            return;
        }
        if (now.isBefore(startTime)) {
            status = AuctionStatus.OPEN;
            return;
        }
        if (now.isBefore(endTime)) {
            status = AuctionStatus.RUNNING;
            return;
        }
        status = AuctionStatus.FINISHED;
        winnerBidderId = leadingBidderId;
    }

    public synchronized boolean placeBid(BidTransaction bid, LocalDateTime now) {
        refreshStatus(now);
        if (status != AuctionStatus.RUNNING) {
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
        if (secondsLeft <= antiSnipingThresholdSeconds) {
            endTime = endTime.plusSeconds(antiSnipingExtensionSeconds);
        }

        return true;
    }

    public synchronized boolean updateListingDetails(
            String title,
            String description,
            BigDecimal newStartingPrice,
            BigDecimal newMinimumBidIncrement,
            LocalDateTime newStartTime,
            LocalDateTime newEndTime
    ) {
        refreshStatus(LocalDateTime.now());
        if (status != AuctionStatus.OPEN) {
            return false;
        }
        if (title == null || title.isBlank()
                || description == null || description.isBlank()
                || newStartingPrice == null || newStartingPrice.signum() < 0
                || newMinimumBidIncrement == null || newMinimumBidIncrement.signum() <= 0
                || newStartTime == null || newEndTime == null
                || !newEndTime.isAfter(newStartTime)) {
            return false;
        }

        item.updateDetails(title, description, newStartingPrice);
        startingPrice = newStartingPrice;
        minimumBidIncrement = newMinimumBidIncrement;
        startTime = newStartTime;
        endTime = newEndTime;
        currentPrice = newStartingPrice;
        leadingBidderId = null;
        winnerBidderId = null;
        bidHistory.clear();
        return true;
    }

    public synchronized void registerAutoBid(AutoBidConfig config) {
        if (config == null || config.getBidderId() == null) {
            return;
        }
        autoBidRegistry.put(config.getBidderId(), config);
    }

    public synchronized void finish() {
        status = AuctionStatus.FINISHED;
        winnerBidderId = leadingBidderId;
    }

    public synchronized boolean markPaid() {
        if (status != AuctionStatus.FINISHED || winnerBidderId == null) {
            return false;
        }
        status = AuctionStatus.PAID;
        return true;
    }

    public synchronized boolean cancel() {
        if (status == AuctionStatus.PAID) {
            return false;
        }
        status = AuctionStatus.CANCELED;
        return true;
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
                copyItem(item),
                startingPrice,
                minimumBidIncrement,
                currentPrice,
                leadingBidderId,
                winnerBidderId,
                status
        );
        copy.bidHistory.addAll(bidHistory);
        copy.autoBidRegistry.putAll(autoBidRegistry);
        copy.antiSnipingThresholdSeconds = antiSnipingThresholdSeconds;
        copy.antiSnipingExtensionSeconds = antiSnipingExtensionSeconds;
        return copy;
    }

    private Item copyItem(Item source) {
        if (source == null) {
            throw new IllegalStateException("Auction item must not be null.");
        }
        if (source instanceof Electronics electronics) {
            return new Electronics(
                    electronics.getId(),
                    electronics.getTitle(),
                    electronics.getDescription(),
                    electronics.getBasePrice(),
                    electronics.getBrand(),
                    electronics.getModel(),
                    electronics.getWarrantyMonths()
            );
        }
        if (source instanceof Vehicle vehicle) {
            return new Vehicle(
                    vehicle.getId(),
                    vehicle.getTitle(),
                    vehicle.getDescription(),
                    vehicle.getBasePrice(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.getYearCreated()
            );
        }
        if (source instanceof Art art) {
            return new Art(
                    art.getId(),
                    art.getTitle(),
                    art.getDescription(),
                    art.getBasePrice(),
                    art.getAuthor(),
                    art.getYearCreated()
            );
        }
        throw new IllegalStateException("Unsupported item type: " + source.getClass().getName());
    }
}
