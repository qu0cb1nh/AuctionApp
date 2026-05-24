package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class WatchListChangedMessage extends Message {
    private String auctionId;
    private boolean watched;

    public WatchListChangedMessage() {
        super(MessageType.WATCH_LIST_CHANGED);
    }

    public WatchListChangedMessage(String auctionId, boolean watched) {
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
