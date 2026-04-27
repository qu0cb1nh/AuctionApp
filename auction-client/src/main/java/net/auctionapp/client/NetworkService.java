package net.auctionapp.client;

import javafx.application.Platform;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.PingMessage;
import net.auctionapp.common.utils.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class NetworkService {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final Map<MessageType, List<Consumer<Message>>> messageHandlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Message>> pendingRequests = new ConcurrentHashMap<>();

    private PrintWriter out;
    private Socket socket;
    private BufferedReader in;

    private ScheduledExecutorService heartbeatScheduler;

    public void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Thread listenerThread = new Thread(this::listenForServerMessages);
            listenerThread.setDaemon(true);
            listenerThread.start();

            sendHeartbeat();
        } catch (IOException e) {
            System.err.println("Cannot connect to server: " + e.getMessage());
        }
    }

    private void sendHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                sendMessage(new PingMessage());
            } catch (Exception e) {
                System.err.println("Failed to send heartbeat ping.");
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
    }

    public void sendMessage(Message message) {
        if (message == null) {
            System.err.println("Cannot send a null message.");
            return;
        }
        if (out == null || socket == null || socket.isClosed()) {
            System.err.println("Cannot send message: client is not connected.");
            return;
        }

        if (!sendJson(JsonUtil.toJson(message))) {
            System.err.println("Failed to send message to server.");
        }
    }

    public CompletableFuture<Message> sendRequest(Message request) {
        return sendRequest(request, DEFAULT_REQUEST_TIMEOUT);
    }

    public CompletableFuture<Message> sendRequest(Message request, Duration timeout) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Request cannot be null."));
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Timeout must be greater than zero."));
        }
        if (out == null || socket == null || socket.isClosed()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected."));
        }

        String requestId = request.getMessageId();
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
            request.setMessageId(requestId);
        }
        request.setCorrelationId(null);

        CompletableFuture<Message> future = new CompletableFuture<>();
        CompletableFuture<Message> existing = pendingRequests.putIfAbsent(requestId, future);
        if (existing != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Duplicate request id: " + requestId));
        }

        final String finalRequestId = requestId;
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((message, throwable) -> pendingRequests.remove(finalRequestId));

        if (!sendJson(JsonUtil.toJson(request))) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(new IllegalStateException("Failed to send request to server."));
        }

        return future;
    }

    public void addMessageHandler(MessageType type, Consumer<Message> handler) {
        if (type == null || handler == null) {
            return;
        }

        messageHandlers
                .computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(handler);
    }

    public void removeMessageHandler(MessageType type, Consumer<Message> handler) {
        if (type == null || handler == null) {
            return;
        }

        List<Consumer<Message>> handlers = messageHandlers.get(type);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    public void shutdown() {
        stopHeartbeat();
        failPendingRequests("Network service is shutting down.");

        if (socket == null || socket.isClosed()) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("Failed to close client socket: " + e.getMessage());
        }
    }

    private void listenForServerMessages() {
        try {
            String jsonString;
            while ((jsonString = in.readLine()) != null) {
                final Message message = JsonUtil.fromJson(jsonString);
                Platform.runLater(() -> handleMessagesFromServer(message));
            }
        } catch (IOException e) {
            System.err.println("Disconnected from server: " + e.getMessage());
        } finally {
            // If the listening loop breaks (server closed or network dropped),
            // ensure the heartbeat stops so we don't keep trying to ping a dead socket.
            stopHeartbeat();
            failPendingRequests("Disconnected from server.");
        }
    }

    private void handleMessagesFromServer(Message message) {
        if (message == null) {
            return;
        }

        String correlationId = message.getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            CompletableFuture<Message> pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.complete(message);
                return;
            }
        }

        // If the message doesn't have a correlationId, route the message to message handlers
        routeMessageToHandlers(message);
    }

    private void routeMessageToHandlers(Message message) {
        List<Consumer<Message>> handlers = messageHandlers.get(message.getType());
        if(handlers == null) {
            return;
        }
        for (Consumer<Message> handler : handlers) {
            handler.accept(message);
        }
    }

    private void failPendingRequests(String reason) {
        IllegalStateException exception = new IllegalStateException(reason);
        for (CompletableFuture<Message> future : pendingRequests.values()) {
            future.completeExceptionally(exception);
        }
        pendingRequests.clear();
    }

    private synchronized boolean sendJson(String jsonPayload) {
        if (out == null || socket == null || socket.isClosed()) {
            return false;
        }
        out.println(jsonPayload);
        return !out.checkError();
    }
}
