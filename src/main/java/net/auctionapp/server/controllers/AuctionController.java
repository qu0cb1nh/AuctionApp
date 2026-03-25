package net.auctionapp.server.controllers;

import net.auctionapp.common.messages.*;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.models.Auction;
import net.auctionapp.common.models.User;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.ServerApp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionController {
    private static AuctionController instance;
    private final Map<String, Auction> auctions = new ConcurrentHashMap<>();

    private AuctionController() {
        auctions.put("ITEM_123", new Auction("ITEM_123", "Van Gogh Painting", 1000));
        auctions.put("ITEM_456", new Auction("ITEM_456", "Macbook Pro 2023", 2000));
    }

    public static synchronized AuctionController getInstance() {
        if (instance == null) {
            instance = new AuctionController();
        }
        return instance;
    }

    /**
     * Processes messages received from the client.
     * @param message       The message object deserialized from JSON.
     * @param clientHandler The handler for the client that sent the message.
     */
    public void processMessage(Message message, ClientHandler clientHandler) {
        if (message == null) {
            return; // Ignore if the message is invalid
        }

        if (message.getType() == MessageType.BID_REQUEST) {
            processBid((BidRequestMessage) message, clientHandler);
        }
        // Add other cases for other message types in the future
    }

    /**
     * Processes a bid request. Called by processMessage.
     * This method is synchronized to ensure thread safety.
     */
    private synchronized void processBid(BidRequestMessage bidRequestMessage, ClientHandler clientHandler) {
        Auction auction = auctions.get(bidRequestMessage.getItemId());

        if (auction == null) {
            ErrorMessage error = new ErrorMessage("Item not found.");
            clientHandler.sendMessage(JsonUtil.toJson(error));
            return;
        }

        if (bidRequestMessage.getPrice() > auction.getCurrentPrice()) {
            User bidder = new User(bidRequestMessage.getUserName());
            auction.setCurrentPrice(bidRequestMessage.getPrice());
            auction.setLeadingBidder(bidder);

            // Notify all clients about the new price
            PriceUpdateMessage update = new PriceUpdateMessage(
                    auction.getItemId(),
                    auction.getCurrentPrice(),
                    bidder.getName()
            );
            ServerApp.broadcast(JsonUtil.toJson(update));
        } else {
            // Only send an error to the bidding client
            // Instead of a generic ERROR, send BID_REJECTED for clarity
            ErrorMessage error = new ErrorMessage("Your bid must be higher than the current price (" + auction.getCurrentPrice() + ").");
            clientHandler.sendMessage(JsonUtil.toJson(error));
        }
    }
}
