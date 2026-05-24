package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.GetWatchListRequestMessage;
import net.auctionapp.common.messages.types.UpdateWatchListRequestMessage;

public final class WatchListService {
    private static final WatchListService INSTANCE = new WatchListService();

    private WatchListService() {
    }

    public static WatchListService getInstance() {
        return INSTANCE;
    }

    public void requestWatchList(MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetWatchListRequestMessage(), callback);
    }

    public void updateWatched(String auctionId, boolean watched, MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new UpdateWatchListRequestMessage(auctionId, watched), callback);
    }
}
