package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.services.AuthService;
import net.auctionapp.server.services.AuctionService;
import net.auctionapp.server.services.NotificationService;
import net.auctionapp.server.services.UserService;
import net.auctionapp.server.services.WalletService;
import net.auctionapp.server.services.WatchListService;
import net.auctionapp.server.managers.SessionManager;
import net.auctionapp.server.messages.MessageRouter;
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
    private final AuctionService auctionService;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final MessageRouter messageRouter;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile String authenticatedUserId;
    private volatile UserRole authenticatedRole;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.auctionService = AuctionService.getInstance();
        this.authService = AuthService.getInstance();
        this.sessionManager = SessionManager.getInstance();
        this.messageRouter = new MessageRouter(
                authService,
                auctionService,
                UserService.getInstance(),
                NotificationService.getInstance(),
                WalletService.getInstance(),
                WatchListService.getInstance()
        );
    }

    @Override
    public void run() {
        try (Socket clientSocket = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            clientSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            synchronized (this) {
                in = reader;
                out = writer;
            }
            ServerApp.registerClient(this);
            LOGGER.info("Client connected from {}", clientSocket.getInetAddress());

            String jsonString;
            while ((jsonString = reader.readLine()) != null) {
                LOGGER.debug("Received from {}: {}", clientSocket.getInetAddress(), jsonString);

                Message message = null;
                try {
                    message = JsonUtil.fromJson(jsonString);
                    if (message == null) {
                        LOGGER.warn("Received null message after JSON deserialization from {}", clientSocket.getInetAddress());
                        continue;
                    }

                    handleMessagesFromClient(message);
                } catch (Exception e) {
                    LOGGER.error("Error processing message from {}: {}", clientSocket.getInetAddress(), jsonString, e);
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

    public synchronized void closeConnection() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("Closing connection for client {}", socket.getInetAddress());
        try {
            socket.close();
        } catch (IOException e) {
            LOGGER.warn("Error while closing socket for {}: {}", socket.getInetAddress(), e.getMessage());
        } finally {
            out = null;
            in = null;
            auctionService.removeSubscriber(this);
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

    public void authenticate(String userId, UserRole role) {
        this.authenticatedUserId = userId;
        this.authenticatedRole = role;
    }

    public synchronized void logout() {
        authenticatedUserId = null;
        authenticatedRole = null;
        sessionManager.unbindSession(this);
    }

    public String getAuthenticatedId() {
        return authenticatedUserId;
    }

    public void ensureAuthenticated() {
        if (authenticatedUserId == null || authenticatedRole == null) {
            throw new AuctionAppException("You must log in before using auction features.");
        }
        authService.requireActiveUserById(authenticatedUserId);
    }

    private void handleMessagesFromClient(Message message) {
        if (shouldEnforceBannedAccess(message.getType()) && !enforceAuthenticatedSessionAccess(message)) {
            return;
        }
        messageRouter.dispatch(message, this);
    }

    private boolean shouldEnforceBannedAccess(MessageType messageType) {
        return messageType != MessageType.LOGIN_REQUEST
                && messageType != MessageType.REGISTER_REQUEST
                && messageType != MessageType.PING;
    }

    private boolean enforceAuthenticatedSessionAccess(Message message) {
        if (authenticatedUserId == null || authenticatedRole == null) {
            return true;
        }
        try {
            authService.requireActiveUserById(authenticatedUserId);
            return true;
        } catch (AuctionAppException e) {
            sendResponse(new ErrorMessage(e.getMessage()), message);
            closeConnection();
            return false;
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
