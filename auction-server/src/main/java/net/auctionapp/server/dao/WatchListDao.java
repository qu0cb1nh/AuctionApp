package net.auctionapp.server.dao;

import java.util.List;

public interface WatchListDao {
    List<String> findAuctionIdsByUserId(String userId);

    void setWatched(String userId, String auctionId, boolean watched);
}
