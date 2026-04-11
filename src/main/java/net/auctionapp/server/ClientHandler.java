package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.*;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.AuctionManager;
import net.auctionapp.server.managers.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final AuctionManager auctionManager;
    private final AuthManager authManager;
    private final SessionManager sessionManager;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private String authenticatedUserId;
    private String authenticatedRole;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.auctionManager = AuctionManager.getInstance();
        this.authManager = AuthManager.getInstance();
        this.sessionManager = SessionManager.getInstance();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            logger.info("Client connected from {}", socket.getInetAddress());

            String jsonString;
            while ((jsonString = in.readLine()) != null) {
                logger.debug("Received from {}: {}", socket.getInetAddress(), jsonString);

                try {
                    Message message = JsonUtil.fromJson(jsonString);
                    if (message == null) {
                        logger.warn("Received null message after JSON deserialization from {}", socket.getInetAddress());
                        continue;
                    }

                    handleMessagesFromClient(message);
                } catch (Exception e) {
                    logger.error("Error processing message from {}: {}", socket.getInetAddress(), jsonString, e);
                    sendMessage(JsonUtil.toJson(new ErrorMessage("Error processing your request: " + e.getMessage())));
                }
            }
        } catch (IOException e) {
            logger.info("Client at {} disconnected.", socket.getInetAddress());
        } finally {
            closeConnection();
        }
    }

    public void closeConnection() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        logger.info("Closing connection for client {}", socket.getInetAddress());
        try {
            socket.close();
        } catch (IOException e) {
            logger.warn("Error while closing socket for {}: {}", socket.getInetAddress(), e.getMessage());
        } finally {
            sessionManager.unbindSession(this);
            ServerApp.unregisterClient(this);
        }
    }

    public boolean sendMessage(String jsonMessage) {
        if (closed.get() || out == null || socket.isClosed()) {
            logger.warn("Attempted to send message to a closed client {}.", socket.getInetAddress());
            closeConnection();
            return false;
        }

        out.println(jsonMessage);
        if (out.checkError()) {
            logger.error("PrintWriter error for client {}. Closing connection.", socket.getInetAddress());
            closeConnection();
            return false;
        }
        logger.debug("Sent to {}: {}", socket.getInetAddress(), jsonMessage);
        return true;
    }

    public void authenticate(String userId, String role) {
        this.authenticatedUserId = userId;
        this.authenticatedRole = role;
    }

    public String getAuthenticatedId() {
        return authenticatedUserId;
    }

    public void ensureAuthenticated() {
        if (authenticatedUserId == null || authenticatedRole == null) {
            throw new AuctionAppException("You must log in before using auction features.");
        }
    }

    private void handleMessagesFromClient(Message message) {
        auctionManager.broadcastEndedAuctions();
        switch (message.getType()) {
            case LOGIN_REQUEST:
                authManager.handleLogin((LoginRequestMessage) message, this);
                break;
            case REGISTER_REQUEST:
                authManager.handleRegister((RegisterRequestMessage) message, this);
                break;
            case GET_AUCTION_LIST_REQUEST:
                auctionManager.handleGetAuctionList(this);
                break;
            case GET_AUCTION_DETAILS_REQUEST:
                auctionManager.handleGetAuctionDetails((GetAuctionDetailsRequestMessage) message, this);
                break;
            case CREATE_ITEM_REQUEST:
                auctionManager.handleCreateItem((CreateItemRequestMessage) message, this);
                break;
            case BID_REQUEST:
                auctionManager.handleBidRequest((BidRequestMessage) message, this);
                break;
            default:
                logger.warn("Received unsupported message type: {} from {}", message.getType(), socket.getInetAddress());
                sendMessage(JsonUtil.toJson(new ErrorMessage("Unsupported message type.")));
                break;
        }
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
