package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionEndedMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.BidResultMessage;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.models.items.*;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.AuctionManager;
import net.auctionapp.common.models.auction.Auction;

import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final AuctionManager auctionManager;
    private final AuthManager authManager;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private String authenticatedUsername;
    private String authenticatedRole;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.auctionManager = AuctionManager.getInstance();
        this.authManager = AuthManager.getInstance();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String jsonString;
            while ((jsonString = in.readLine()) != null) {
                System.out.println("Received JSON from client " + socket.getInetAddress() + ": " + jsonString);

                try {
                    Message message = JsonUtil.fromJson(jsonString);
                    if (message == null) {
                        continue;
                    }

                    handleMessagesFromClient(message);
                } catch (Exception e) {
                    System.err.println("Error processing JSON from client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("A bidder at " + socket.getInetAddress() + " has disconnected.");
        } finally {
            closeConnection();
        }
    }

    public void closeConnection() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            socket.close();
        } catch (IOException e) {
            // Ignore close errors during disconnect/shutdown.
        } finally {
            ServerApp.unregisterClient(this);
        }
    }

    /**
     * Sends a message (already converted to JSON) to this client.
     *
     * @param jsonMessage The message as a JSON string.
     * @return true if the write succeeds, false if this client is not writable.
     */
    public boolean sendMessage(String jsonMessage) {
        if (closed.get() || out == null || socket.isClosed()) {
            closeConnection();
            return false;
        }

        out.println(jsonMessage);
        if (out.checkError()) {
            closeConnection();
            return false;
        }

        return true;
    }

    public void authenticate(String username, String role) {
        this.authenticatedUsername = username;
        this.authenticatedRole = role;
    }

    private void handleMessagesFromClient(Message message) {
        broadcastEndedAuctions();
        switch (message.getType()) {
            case LOGIN_REQUEST:
                authManager.handleLogin((LoginRequestMessage) message, this);
                break;
            case REGISTER_REQUEST:
                authManager.handleRegister((RegisterRequestMessage) message, this);
                break;
            case GET_AUCTION_LIST_REQUEST:
                handleGetAuctionList((GetAuctionListRequestMessage) message);
                break;
            case GET_AUCTION_DETAILS_REQUEST:
                handleGetAuctionDetails((GetAuctionDetailsRequestMessage) message);
                break;
            case CREATE_ITEM_REQUEST:
                handleCreateItem((CreateItemRequestMessage) message);
                break;
            case BID_REQUEST:
                handleBidRequest((BidRequestMessage) message);
                break;
            default:
                sendMessage(JsonUtil.toJson(new ErrorMessage("Unsupported message type.")));
                break;
        }
    }

    private void handleGetAuctionList(GetAuctionListRequestMessage message) {
        sendMessage(JsonUtil.toJson(new AuctionListResponseMessage(auctionManager.getAuctionSummaries())));
    }

    private void handleGetAuctionDetails(GetAuctionDetailsRequestMessage message) {
        try {
            Auction auction = auctionManager.getAuctionById(message.getAuctionId())
                    .orElseThrow(() -> new AuctionAppException("Auction not found."));
            AuctionDetailsResponseMessage response = new AuctionDetailsResponseMessage(
                    auction.getId(),
                    auction.getSellerId(),
                    auction.getItem().getTitle(),
                    auction.getItem().getDescription(),
                    auction.getStartingPrice(),
                    auction.getCurrentPrice(),
                    auction.getMinimumNextBid(),
                    auction.getStatus(),
                    auction.getLeadingBidderId(),
                    auction.getWinnerBidderId(),
                    auction.getStartTime(),
                    auction.getEndTime(),
                    auctionManager.getBidViews(auction.getId())
            );
            sendMessage(JsonUtil.toJson(response));
        } catch (AuctionAppException e) {
            sendMessage(JsonUtil.toJson(new ErrorMessage(e.getMessage())));
        }
    }

    private void handleCreateItem(CreateItemRequestMessage message) {
        try {
            ensureAuthenticated();
            Item item = createItemFromRequest(message);
            Auction auction = auctionManager.createAuction(
                    authenticatedUsername.toLowerCase(),
                    item,
                    message.getStartingPrice(),
                    message.getMinimumBidIncrement(),
                    message.getStartTime(),
                    message.getEndTime()
            );
            handleGetAuctionDetails(new GetAuctionDetailsRequestMessage(auction.getId()));
        } catch (AuctionAppException e) {
            sendMessage(JsonUtil.toJson(new ErrorMessage(e.getMessage())));
        }
    }

    private void handleBidRequest(BidRequestMessage message) {
        try {
            ensureAuthenticated();
            Auction auction = auctionManager.getAuctionById(message.getItemId())
                    .orElseThrow(() -> new AuctionAppException("Auction not found."));
            auctionManager.submitBid(
                    auction.getId(),
                    authenticatedUsername.toLowerCase(),
                    BigDecimal.valueOf(message.getPrice())
            );
            Auction updatedAuction = auctionManager.getAuctionById(auction.getId())
                    .orElseThrow(() -> new AuctionAppException("Auction not found."));
            sendMessage(JsonUtil.toJson(new BidResultMessage(
                    MessageType.BID_ACCEPTED,
                    updatedAuction.getId(),
                    updatedAuction.getCurrentPrice(),
                    updatedAuction.getLeadingBidderId(),
                    "Bid accepted."
            )));
            ServerApp.broadcast(JsonUtil.toJson(new PriceUpdateMessage(
                    updatedAuction.getId(),
                    updatedAuction.getCurrentPrice().doubleValue(),
                    updatedAuction.getLeadingBidderId()
            )));
            broadcastEndedAuctions();
        } catch (AuctionAppException e) {
            sendMessage(JsonUtil.toJson(new BidResultMessage(
                    MessageType.BID_REJECTED,
                    message.getItemId(),
                    null,
                    null,
                    e.getMessage()
            )));
        }
    }

    private void broadcastEndedAuctions() {
        for (Auction auction : auctionManager.collectNewlyEndedAuctions()) {
            ServerApp.broadcast(JsonUtil.toJson(new AuctionEndedMessage(
                    auction.getId(),
                    auction.getWinnerBidderId(),
                    auction.getCurrentPrice()
            )));
        }
    }

    private void ensureAuthenticated() {
        if (authenticatedUsername == null || authenticatedRole == null) {
            throw new AuctionAppException("You must log in before using auction features.");
        }
    }

    private Item createItemFromRequest(CreateItemRequestMessage message) {
        ItemType type = message.getItemType();
        ItemFactory factory;
        switch (type) {
            case ART -> {
                factory = new ArtFactory();
            }
            case ELECTRONICS -> {
                factory = new ElectronicsFactory();
            }
            case VEHICLE ->  {
                 factory = new VehicleFactory();
            }
            default -> throw new AuctionAppException("Unsupported item type.");
        }
        return factory.createItem(message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientHandler that = (ClientHandler) o;
        return socket.equals(that.socket);
    }

    @Override
    public int hashCode() {
        return socket.hashCode();
    }
}
