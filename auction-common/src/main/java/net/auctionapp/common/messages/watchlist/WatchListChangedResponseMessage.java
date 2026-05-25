package net.auctionapp.common.messages.watchlist;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class WatchListChangedResponseMessage extends Message {
    private String auctionId;
    private boolean watched;

    public WatchListChangedResponseMessage() {
        super(MessageType.WATCH_LIST_CHANGED);
    }

    public WatchListChangedResponseMessage(String auctionId, boolean watched) {
        super(MessageType.WATCH_LIST_CHANGED);
        this.auctionId = auctionId;
        this.watched = watched;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public boolean isWatched() {
        return watched;
    }
}
