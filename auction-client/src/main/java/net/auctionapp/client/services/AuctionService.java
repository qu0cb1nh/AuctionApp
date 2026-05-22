package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.CancelAuctionRequestMessage;
import net.auctionapp.common.messages.types.CloseAuctionRequestMessage;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.types.UpdateAuctionRequestMessage;

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
}
