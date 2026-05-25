package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.auction.BidRequestMessage;
import net.auctionapp.common.messages.auction.CancelAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CloseAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.auction.ObserveAuctionRequestMessage;
import net.auctionapp.common.messages.auction.UpdateAuctionRequestMessage;

import java.math.BigDecimal;

public final class AuctionService {
    private static final AuctionService INSTANCE = new AuctionService();

    public static AuctionService getInstance() {
        return INSTANCE;
    }

    private AuctionService() {
    }

    public void requestAuctionList(MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetAuctionListRequestMessage(), callback);
    }

    public void requestAuctionDetails(String auctionId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetAuctionDetailsRequestMessage(auctionId), callback);
    }

    public void createAuction(CreateItemRequestMessage request, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(request, callback);
    }

    public void updateAuction(UpdateAuctionRequestMessage request, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(request, callback);
    }

    public void cancelAuction(String auctionId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new CancelAuctionRequestMessage(auctionId), callback);
    }

    public void closeAuction(String auctionId, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new CloseAuctionRequestMessage(auctionId), callback);
    }

    public void placeBid(String auctionId, BigDecimal amount, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new BidRequestMessage(auctionId, amount), callback);
    }

    public void observeAuction(String auctionId, boolean observing) {
        NetworkService.getInstance().sendMessage(new ObserveAuctionRequestMessage(auctionId, observing));
    }
}
