package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auth.ForcedLogoutResponseMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.managers.SessionManager;
import net.auctionapp.server.messages.MessageRouter;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.AuctionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);
    private static final int SOCKET_READ_TIMEOUT_MILLIS = 60_000;

    private final Socket socket;
    private final AuctionManager auctionManager;
    private final AuthManager authManager;
    private final SessionManager sessionManager;
    private final MessageRouter messageRouter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private PrintWriter out;
    private volatile String authenticatedUserId;
    private volatile UserRole authenticatedRole;

    public ClientHandler(Socket socket, MessageRouter messageRouter) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.messageRouter = Objects.requireNonNull(messageRouter, "messageRouter must not be null");
        this.auctionManager = AuctionManager.getInstance();
        this.authManager = AuthManager.getInstance();
        this.sessionManager = SessionManager.getInstance();
    }

    @Override
    public void run() {
        try (Socket clientSocket = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            clientSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);

            synchronized (this) {
                out = writer;
            }

            ServerApp.registerClient(this);
            LOGGER.info("Client connected from {}", clientSocket.getInetAddress());

            String jsonString;
            while ((jsonString = reader.readLine()) != null) {
                handleRawMessage(jsonString);
            }
        } catch (IOException e) {
            if (!closed.get()) {
                LOGGER.info("Client at {} disconnected: {}", socket.getInetAddress(), e.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    private void handleRawMessage(String jsonString) {
        LOGGER.debug("Received from {}: {}", socket.getInetAddress(), jsonString);

        Message request = null;
        try {
            request = JsonUtil.fromJson(jsonString);

            if (request == null) {
                LOGGER.warn("Received null message after JSON deserialization from {}", socket.getInetAddress());
                return;
            }
            if (shouldEnforceBannedAccess(request.getType()) && !enforceAuthenticatedSessionAccess()) {
                return;
            }
            messageRouter.dispatch(request, this);
        } catch (RuntimeException e) {
            LOGGER.error("Error processing message from {}: {}", socket.getInetAddress(), jsonString, e);
            sendResponse(new ErrorResponseMessage("Unable to process request."), request);
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
            auctionManager.removeSubscriber(this);
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
        return message != null && sendMessage(JsonUtil.toJson(message));
    }

    public void sendResponse(Message response, Message request) {
        if (response == null) {
            return;
        }

        if (request != null && request.getMessageId() != null && !request.getMessageId().isBlank()) {
            response.setCorrelationId(request.getMessageId());
        }

        sendMessage(response);
    }

    public void authenticate(String userId, UserRole role) {
        authenticatedUserId = userId;
        authenticatedRole = role;
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
            throw new AuthenticationException("You must log in before using auction features.");
        }

        authManager.requireActiveUserById(authenticatedUserId);
    }

    private boolean shouldEnforceBannedAccess(MessageType messageType) {
        return messageType != MessageType.LOGIN_REQUEST
                && messageType != MessageType.REGISTER_REQUEST
                && messageType != MessageType.PING;
    }

    private boolean enforceAuthenticatedSessionAccess() {
        if (authenticatedUserId == null || authenticatedRole == null) {
            return true;
        }

        try {
            authManager.requireActiveUserById(authenticatedUserId);
            return true;
        } catch (AuthenticationException | NotFoundException e) {
            sendMessage(new ForcedLogoutResponseMessage(e.getMessage()));
            closeConnection();
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientHandler that)) return false;
        return socket.equals(that.socket);
    }

    @Override
    public int hashCode() {
        return socket.hashCode();
    }
}