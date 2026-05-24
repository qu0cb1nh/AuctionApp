package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class UpdateWatchListRequestMessage extends Message {
    private String auctionId;
    private boolean watched;

    public UpdateWatchListRequestMessage() {
        super(MessageType.UPDATE_WATCH_LIST_REQUEST);
    }

    public UpdateWatchListRequestMessage(String auctionId, boolean watched) {
        super(MessageType.UPDATE_WATCH_LIST_REQUEST);
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
