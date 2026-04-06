package net.auctionapp.server.controllers;

import net.auctionapp.common.models.auction.Auction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionController {
    private static AuctionController INSTANCE = null;
    private final Map<String, Auction> auctions = new ConcurrentHashMap<>();

    private AuctionController() {
    }

    public static synchronized AuctionController getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new AuctionController();
        }
        return INSTANCE;
    }
}
