package net.auctionapp.common.messages.watchlist;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetWatchListRequestMessage extends Message {
    public GetWatchListRequestMessage() {
        super(MessageType.GET_WATCH_LIST_REQUEST);
    }
}
