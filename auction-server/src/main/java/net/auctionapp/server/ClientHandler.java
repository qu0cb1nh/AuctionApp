package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.*;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.AuctionManager;
import net.auctionapp.server.managers.NotificationManager;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);
    private static final int SOCKET_READ_TIMEOUT_MILLIS = 60_000;
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final AuctionManager auctionManager;
    private final AuthManager authManager;
    private final NotificationManager notificationManager;
    private final SessionManager sessionManager;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile String authenticatedUserId;
    private volatile String authenticatedRole;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.auctionManager = AuctionManager.getInstance();
        this.authManager = AuthManager.getInstance();
        this.notificationManager = NotificationManager.getInstance();
        this.sessionManager = SessionManager.getInstance();
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            LOGGER.info("Client connected from {}", socket.getInetAddress());

            String jsonString;
            while ((jsonString = in.readLine()) != null) {
                LOGGER.debug("Received from {}: {}", socket.getInetAddress(), jsonString);

                Message message = null;
                try {
                    message = JsonUtil.fromJson(jsonString);
                    if (message == null) {
                        LOGGER.warn("Received null message after JSON deserialization from {}", socket.getInetAddress());
                        continue;
                    }

                    handleMessagesFromClient(message);
                } catch (Exception e) {
                    LOGGER.error("Error processing message from {}: {}", socket.getInetAddress(), jsonString, e);
                    sendResponse(new ErrorMessage("Error processing your request: " + e.getMessage()), message);
                }
            }
        } catch (IOException e) {
            LOGGER.info("Client at {} disconnected.", socket.getInetAddress());
            LOGGER.info(e.getMessage());
        } finally {
            closeConnection();
        }
    }

    public void closeConnection() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("Closing connection for client {}", socket.getInetAddress());
        try {
            socket.close();
        } catch (IOException e) {
            LOGGER.warn("Error while closing socket for {}: {}", socket.getInetAddress(), e.getMessage());
        } finally {
            sessionManager.unbindSession(this);
            ServerApp.unregisterClient(this);
        }
    }

    public synchronized boolean sendMessage(String jsonMessage) {
        if (closed.get() || out == null || socket.isClosed()) {
            LOGGER.warn("Attempted to send message to a closed client {}.", socket.getInetAddress());
            closeConnection();
            return false;
        }

        out.println(jsonMessage);
        if (out.checkError()) {
            LOGGER.error("PrintWriter error for client {}. Closing connection.", socket.getInetAddress());
            closeConnection();
            return false;
        }
        LOGGER.debug("Sent to {}: {}", socket.getInetAddress(), jsonMessage);
        return true;
    }

    public boolean sendMessage(Message message) {
        if (message == null) {
            return false;
        }
        return sendMessage(JsonUtil.toJson(message));
    }

    public boolean sendResponse(Message response, Message request) {
        if (response == null) {
            return false;
        }
        if (request != null && request.getMessageId() != null && !request.getMessageId().isBlank()) {
            response.setCorrelationId(request.getMessageId());
        }
        return sendMessage(response);
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
            case PING:
                sendResponse(new PongMessage(), message);
                break;
            case LOGIN_REQUEST:
                authManager.handleLogin((LoginRequestMessage) message, this);
                break;
            case REGISTER_REQUEST:
                authManager.handleRegister((RegisterRequestMessage) message, this);
                break;
            case GET_AUCTION_LIST_REQUEST:
                auctionManager.handleGetAuctionList((GetAuctionListRequestMessage) message, this);
                break;
            case GET_AUCTION_DETAILS_REQUEST:
                auctionManager.handleGetAuctionDetails((GetAuctionDetailsRequestMessage) message, this);
                break;
            case GET_NOTIFICATIONS_REQUEST:
                notificationManager.handleGetNotifications((GetNotificationsRequestMessage) message, this);
                break;
            case CREATE_ITEM_REQUEST:
                auctionManager.handleCreateItem((CreateItemRequestMessage) message, this);
                break;
            case BID_REQUEST:
                auctionManager.handleBidRequest((BidRequestMessage) message, this);
                break;
            case MARK_NOTIFICATION_READ_REQUEST:
                notificationManager.handleMarkNotificationRead((MarkNotificationReadRequestMessage) message, this);
                break;
            case CLEAR_NOTIFICATIONS_REQUEST:
                notificationManager.handleClearNotifications((ClearNotificationsRequestMessage) message, this);
                break;
            default:
                LOGGER.warn("Received unsupported message type: {} from {}", message.getType(), socket.getInetAddress());
                sendResponse(new ErrorMessage("Unsupported message type."), message);
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
