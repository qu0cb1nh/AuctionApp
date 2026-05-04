package net.auctionapp.client.services;

import javafx.application.Platform;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.PingMessage;
import net.auctionapp.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class NetworkService {
    private static final NetworkService INSTANCE = new NetworkService();
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkService.class);

    private final Map<MessageType, List<MessageListener<? extends Message>>> messageListeners = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Message>> pendingRequests = new ConcurrentHashMap<>();

    private PrintWriter out;
    private Socket socket;
    private BufferedReader in;

    private ScheduledExecutorService heartbeatScheduler;

    private NetworkService() {
    }

    public static NetworkService getInstance() {
        return INSTANCE;
    }

    public synchronized void connect(String host, int port) {
        if (isConnected()) {
            return;
        }
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Thread listenerThread = new Thread(this::listenForServerMessages);
            listenerThread.setName("auction-client-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            sendHeartbeat();
        } catch (IOException e) {
            LOGGER.error("Could not connect to the network service", e);
            closeResourcesQuietly();
        }
    }

    private void sendHeartbeat() {
        // Use a ThreadFactory to create daemon threads.
        ThreadFactory daemonThreadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("auction-client-heartbeat");
            return thread;
        };

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                sendMessage(new PingMessage());
            } catch (Exception e) {
                LOGGER.error("Could not send heartbeat", e);
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
            LOGGER.error("Cannot send a null message.");
            return;
        }
        if (out == null || socket == null || socket.isClosed()) {
            LOGGER.error("Cannot send message: client is not connected.");
            return;
        }

        if (!sendJson(JsonUtil.toJson(message))) {
            LOGGER.error("Failed to send message to server.");
        }
    }

    public void sendRequest(Message request, MessageListener<Message> callback, Consumer<Throwable> onError) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        sendRequest(request)
                .thenAccept(callback::onMessage)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send message to server.", throwable);
                    Platform.runLater(() -> {
                        onError.accept(throwable);
                    });
                    return null;
                });
    }

    public void sendRequest(Message request, MessageListener<Message> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        sendRequest(request)
                .thenAccept(callback::onMessage)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send message to server.", throwable);
                    Platform.runLater(() -> {
                        // TODO: Actually handle the throwable and provide useful feedback to the user
                    });
                    return null;
                });
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

    public <T extends Message> void addMessageListener(MessageType type, MessageListener<T> listener) {
        if (type == null || listener == null) {
            return;
        }

        messageListeners
                .computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    public void removeMessageListener(MessageType type, MessageListener<?> listener) {
        if (type == null || listener == null) {
            return;
        }

        List<MessageListener<? extends Message>> listeners = messageListeners.get(type);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public synchronized void shutdown() {
        stopHeartbeat();
        failPendingRequests("Network service is shutting down.");

        closeResourcesQuietly();
    }

    private void listenForServerMessages() {
        try (BufferedReader reader = this.in){
            String jsonString;
            while ((jsonString = reader.readLine()) != null) {
                try {
                    final Message message = JsonUtil.fromJson(jsonString);
                    Platform.runLater(() -> handleMessagesFromServer(message));
                } catch (Exception e) {
                    LOGGER.warn("Received malformed message from server: {}", jsonString, e);
                }
            }
        } catch (IOException e) {
            if (!"Socket closed".equals(e.getMessage())) {
                LOGGER.warn("Disconnected from server:", e);
            }
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

        routeMessageToListeners(message);
    }

    @SuppressWarnings("unchecked")
    private void routeMessageToListeners(Message message) {
        List<MessageListener<? extends Message>> listeners = messageListeners.get(message.getType());
        if (listeners == null) {
            return;
        }

        for (MessageListener<? extends Message> listener : listeners) {
            // Unchecked cast is necessary because we store listeners in a type-erased way,
            // but it's safe as long as we ensure that listeners are registered with the correct MessageType and Message class.
            ((MessageListener<Message>) listener).onMessage(message);
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
        if (!isConnected() || out == null) {
            return false;
        }
        out.println(jsonPayload);
        return !out.checkError();
    }

    private boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void closeResourcesQuietly() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to close client output stream:", e);
        }
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to close client input stream:", e);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to close client socket: ", e);
        }
        in = null;
        out = null;
        socket = null;
    }
}
