package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;

import java.math.BigDecimal;

public final class AuctionService {
    private static final AuctionService INSTANCE = new AuctionService();

    public static AuctionService getInstance() {
        return INSTANCE;
    }

    private AuctionService() {
    }

    public void requestAuctionList(net.auctionapp.common.messages.MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetAuctionListRequestMessage(), callback);
    }

    public void requestAuctionDetails(String auctionId, net.auctionapp.common.messages.MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetAuctionDetailsRequestMessage(auctionId), callback);
    }

    public void createAuction(CreateItemRequestMessage request, net.auctionapp.common.messages.MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(request, callback);
    }

    public void placeBid(String auctionId, BigDecimal amount, net.auctionapp.common.messages.MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new BidRequestMessage(auctionId, amount.doubleValue()), callback);
    }

    public void addPriceUpdateListener(net.auctionapp.common.messages.MessageListener<PriceUpdateMessage> listener) {
        NetworkService.getInstance().addMessageListener(MessageType.PRICE_UPDATE, listener);
    }

    public void removePriceUpdateListener(net.auctionapp.common.messages.MessageListener<PriceUpdateMessage> listener) {
        NetworkService.getInstance().removeMessageListener(MessageType.PRICE_UPDATE, listener);
    }
}

