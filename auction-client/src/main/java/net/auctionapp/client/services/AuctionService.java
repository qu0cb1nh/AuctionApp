package net.auctionapp.client.services;

import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.auction.BidRequestMessage;
import net.auctionapp.common.messages.auction.CancelBidsRequestMessage;
import net.auctionapp.common.messages.auction.CancelAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CloseAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.auction.GetMyActivityRequestMessage;
import net.auctionapp.common.messages.auction.GetMyListingsRequestMessage;
import net.auctionapp.common.messages.auction.ObserveAuctionRequestMessage;
import net.auctionapp.common.messages.auction.UpdateAuctionRequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public final class AuctionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionService.class);
    private static final AuctionService INSTANCE = new AuctionService();

    public static AuctionService getInstance() {
        return INSTANCE;
    }

    private AuctionService() {
    }

    public void requestAuctionList(MessageListener<Message> callback) {
        LOGGER.debug("Requesting auction list.");
        NetworkService.getInstance().sendRequest(new GetAuctionListRequestMessage(), callback);
    }

    public void requestAuctionDetails(String auctionId, MessageListener<Message> callback) {
        LOGGER.debug("Requesting auction details for auction {}.", auctionId);
        NetworkService.getInstance().sendRequest(new GetAuctionDetailsRequestMessage(auctionId), callback);
    }

    public void requestMyActivity(MessageListener<Message> callback) {
        LOGGER.debug("Requesting current user's auction activity.");
        NetworkService.getInstance().sendRequest(new GetMyActivityRequestMessage(), callback);
    }

    public void requestMyListings(MessageListener<Message> callback) {
        LOGGER.debug("Requesting current user's listings.");
        NetworkService.getInstance().sendRequest(new GetMyListingsRequestMessage(), callback);
    }

    public void createAuction(CreateItemRequestMessage request, MessageListener<Message> callback) {
        LOGGER.info("Submitting create-auction request for item '{}'.", request == null ? null : request.getTitle());
        NetworkService.getInstance().sendRequest(request, callback);
    }

    public void updateAuction(UpdateAuctionRequestMessage request, MessageListener<Message> callback) {
        LOGGER.info("Submitting auction update for auction {}.", request == null ? null : request.getAuctionId());
        NetworkService.getInstance().sendRequest(request, callback);
    }

    public void cancelAuction(String auctionId, MessageListener<Message> callback) {
        LOGGER.info("Submitting auction cancellation for auction {}.", auctionId);
        NetworkService.getInstance().sendRequest(new CancelAuctionRequestMessage(auctionId), callback);
    }

    public void closeAuction(String auctionId, MessageListener<Message> callback) {
        LOGGER.info("Submitting manual close request for auction {}.", auctionId);
        NetworkService.getInstance().sendRequest(new CloseAuctionRequestMessage(auctionId), callback);
    }

    public void placeBid(String auctionId, BigDecimal amount, MessageListener<Message> callback) {
        LOGGER.info("Submitting bid for auction {} with amount {}.", auctionId, amount);
        NetworkService.getInstance().sendRequest(new BidRequestMessage(auctionId, amount), callback);
    }

    public void cancelBids(String auctionId, MessageListener<Message> callback) {
        LOGGER.info("Submitting bid cancellation for auction {}.", auctionId);
        NetworkService.getInstance().sendRequest(new CancelBidsRequestMessage(auctionId), callback);
    }

    public void observeAuction(String auctionId, boolean observing) {
        LOGGER.debug("{} auction updates for auction {}.", observing ? "Subscribing to" : "Unsubscribing from", auctionId);
        NetworkService.getInstance().sendMessage(new ObserveAuctionRequestMessage(auctionId, observing));
    }
}
