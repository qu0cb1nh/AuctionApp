package net.auctionapp.server.managers.auction;

import net.auctionapp.common.dto.AuctionSummary;
import net.auctionapp.common.dto.BidView;
import net.auctionapp.common.messages.auction.AuctionDetailsResponseMessage;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.managers.AuthManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuctionQuery {
    private final Map<String, Auction> auctions;
    private final AuthManager authManager;

    public AuctionQuery(Map<String, Auction> auctions, AuthManager authManager) {
        this.auctions = auctions;
        this.authManager = authManager;
    }

    public List<AuctionSummary> getAuctionSummaries() {
        List<AuctionSummary> result = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            result.add(buildAuctionSummary(auction));
        }
        return result;
    }

    public List<AuctionSummary> getAuctionSummaries(Iterable<String> auctionIds) {
        List<AuctionSummary> result = new ArrayList<>();
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

    public List<AuctionDetailsResponseMessage> getActivityForUser(String userId) {
        return auctions.values().stream()
                .filter(auction -> auction.getActiveBidHistory().stream()
                        .anyMatch(bid -> userId.equals(StringUtil.normalizeString(bid.getBidderId()))))
                .map(this::buildAuctionDetailsResponse)
                .toList();
    }

    public List<AuctionDetailsResponseMessage> getListingsForUser(String userId) {
        return auctions.values().stream()
                .filter(auction -> userId.equals(StringUtil.normalizeString(auction.getSellerId())))
                .map(this::buildAuctionDetailsResponse)
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

    public AuctionSummary buildAuctionSummary(Auction auction) {
        synchronized (auction) {
            Item item = auction.getItem();
            return new AuctionSummary(
                    auction.getId(),
                    item.getTitle(),
                    auction.getCurrentPrice(),
                    auction.getMinimumNextBid(),
                    auction.getStatus(),
                    auction.getLeadingBidderId(),
                    auction.getStartTime(),
                    auction.getEndTime(),
                    item.getImageUrl(),
                    item.getType(),
                    displayUsername(auction.getLeadingBidderId()),
                    auction.getSellerId(),
                    displayUsername(auction.getSellerId())
            );
        }
    }

    public AuctionDetailsResponseMessage buildAuctionDetailsResponse(Auction auction) {
        synchronized (auction) {
            Item item = auction.getItem();
            List<BidView> bids = new ArrayList<>();
            for (BidTransaction bid : auction.getActiveBidHistory()) {
                bids.add(new BidView(bid.getId(), bid.getBidderId(), bid.getAmount(), bid.getTimestamp()));
            }
            return new AuctionDetailsResponseMessage(
                    auction.getId(),
                    auction.getSellerId(),
                    item.getTitle(),
                    item.getDescription(),
                    auction.getStartingPrice(),
                    auction.getCurrentPrice(),
                    auction.getMinimumNextBid(),
                    auction.getStatus(),
                    auction.getLeadingBidderId(),
                    auction.getWinnerBidderId(),
                    auction.getStartTime(),
                    auction.getEndTime(),
                    item.getImageUrl(),
                    item.getType(),
                    bids,
                    displayUsername(auction.getLeadingBidderId()),
                    displayUsername(auction.getWinnerBidderId()),
                    displayUsername(auction.getSellerId())
            );
        }
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
}
