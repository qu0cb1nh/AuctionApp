package net.auctionapp.client.services;

import javafx.application.Platform;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.PingMessage;
import net.auctionapp.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
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
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(5);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(3);
    private static final String CLIENT_NOT_CONNECTED_MESSAGE = "Client is not connected.";
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkService.class);

    private final Map<MessageType, List<MessageListener<? extends Message>>> messageListeners = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Message>> pendingRequests = new ConcurrentHashMap<>();

    private PrintWriter out;
    private Socket socket;
    private BufferedReader in;
    private String configuredHost;
    private int configuredPort;

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledExecutorService reconnectScheduler;
    private volatile boolean shuttingDown;

    private NetworkService() {
    }

    public static NetworkService getInstance() {
        return INSTANCE;
    }

    public synchronized void connect(String host, int port) {
        configuredHost = host;
        configuredPort = port;
        if (isConnected()) {
            return;
        }
        stopHeartbeat();
        closeResourcesQuietly();
        try {
            Socket connectedSocket = new Socket();
            connectedSocket.connect(new InetSocketAddress(host, port), (int) CONNECT_TIMEOUT.toMillis());
            PrintWriter connectedOut = new PrintWriter(connectedSocket.getOutputStream(), true);
            BufferedReader connectedIn = new BufferedReader(new InputStreamReader(connectedSocket.getInputStream()));

            socket = connectedSocket;
            out = connectedOut;
            in = connectedIn;

            stopReconnect();
            Thread listenerThread = new Thread(() -> listenForServerMessages(connectedSocket, connectedIn));
            listenerThread.setName("auction-client-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            sendHeartbeat();
        } catch (IOException e) {
            LOGGER.warn("Could not connect to the server at {}:{}.", host, port);
            closeResourcesQuietly();
            scheduleReconnect();
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
            sendRequest(new PingMessage(), HEARTBEAT_TIMEOUT)
                    .thenAccept(response -> {
                        if (response == null || response.getType() != MessageType.PONG) {
                            LOGGER.warn("Invalid heartbeat response from server.");
                            markDisconnected("Disconnected from server.");
                        }
                    })
                    .exceptionally(throwable -> {
                        Throwable failure = unwrapCompletionException(throwable);
                        if (!isDisconnectedFailure(failure)) {
                            LOGGER.warn("Heartbeat failed: {}", failure.getMessage(), failure);
                        }
                        markDisconnected("Disconnected from server.");
                        return null;
                    });
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
    }

    private synchronized void scheduleReconnect() {
        if (shuttingDown || isConnected()) {
            return;
        }
        if (configuredHost == null || configuredHost.isBlank() || configuredPort <= 0) {
            return;
        }
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            return;
        }
        ThreadFactory daemonThreadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("auction-client-reconnect");
            return thread;
        };
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);
        reconnectScheduler.scheduleWithFixedDelay(() -> {
            if (shuttingDown || isConnected()) {
                stopReconnect();
                return;
            }
            connect(configuredHost, configuredPort);
        }, 0, RECONNECT_DELAY.toSeconds(), TimeUnit.SECONDS);
    }

    private synchronized void stopReconnect() {
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdownNow();
        }
        reconnectScheduler = null;
    }

    public void sendMessage(Message message) {
        if (message == null) {
            LOGGER.error("Cannot send a null message.");
            return;
        }
        if (!isConnected()) {
            scheduleReconnect();
            return;
        }

        if (!sendJson(JsonUtil.toJson(message))) {
            closeResourcesQuietly();
            scheduleReconnect();
        }
    }

    public void sendRequest(Message request, MessageListener<Message> callback, Consumer<Throwable> onError) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        Objects.requireNonNull(onError, "onError must not be null");

        sendRequest(request).whenComplete((message, throwable) -> Platform.runLater(() -> {
            if (throwable == null) {
                callback.onMessage(message);
                return;
            }
            Throwable failure = unwrapCompletionException(throwable);
            logRequestFailure(failure);
            onError.accept(failure);
        }));
    }

    public void sendRequest(Message request, MessageListener<Message> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        sendRequest(request).whenComplete((message, throwable) -> Platform.runLater(() -> {
            if (throwable == null) {
                callback.onMessage(message);
                return;
            }
            Throwable failure = unwrapCompletionException(throwable);
            logRequestFailure(failure);
            callback.onMessage(new ErrorMessage(toUserFacingErrorMessage(failure)));
        }));
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
        if (!isConnected()) {
            scheduleReconnect();
            return CompletableFuture.failedFuture(new IllegalStateException(CLIENT_NOT_CONNECTED_MESSAGE));
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
            closeResourcesQuietly();
            scheduleReconnect();
            future.completeExceptionally(new IllegalStateException(CLIENT_NOT_CONNECTED_MESSAGE));
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
        shuttingDown = true;
        stopHeartbeat();
        stopReconnect();
        failPendingRequests("Network service is shutting down.");

        closeResourcesQuietly();
    }

    private void listenForServerMessages(Socket listenedSocket, BufferedReader socketReader) {
        try (BufferedReader reader = socketReader) {
            String jsonString;
            while ((jsonString = reader.readLine()) != null) {
                try {
                    final Message message = JsonUtil.fromJson(jsonString);
                    handleMessagesFromServer(message);
                } catch (Exception e) {
                    LOGGER.warn("Received malformed message from server: {}", jsonString, e);
                }
            }
        } catch (IOException e) {
            if (!"Socket closed".equals(e.getMessage())) {
                LOGGER.warn("Disconnected from server:", e);
            }
        } finally {
            if (ownsConnection(listenedSocket)) {
                // If the listening loop breaks, make the stale socket impossible to reuse.
                stopHeartbeat();
                failPendingRequests("Disconnected from server.");
                closeResourcesQuietly(listenedSocket);
                scheduleReconnect();
            }
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
            LOGGER.debug("Dropping response for unknown or timed-out request id: {}", correlationId);
            return;
        }

        routeMessageToListeners(message);
    }

    @SuppressWarnings("unchecked")
    private void routeMessageToListeners(Message message) {
        List<MessageListener<? extends Message>> listeners = messageListeners.get(message.getType());
        if (listeners == null) {
            return;
        }

        Platform.runLater(() -> {
            for (MessageListener<? extends Message> listener : listeners) {
                // Unchecked cast is necessary because listeners are keyed by their message type.
                ((MessageListener<Message>) listener).onMessage(message);
            }
        });
    }

    private void failPendingRequests(String reason) {
        IllegalStateException exception = new IllegalStateException(reason);
        for (CompletableFuture<Message> future : pendingRequests.values()) {
            future.completeExceptionally(exception);
        }
        pendingRequests.clear();
    }

    private void markDisconnected(String reason) {
        stopHeartbeat();
        failPendingRequests(reason);
        closeResourcesQuietly();
        scheduleReconnect();
    }

    private synchronized boolean sendJson(String jsonPayload) {
        if (!isConnected() || out == null) {
            return false;
        }
        out.println(jsonPayload);
        return !out.checkError();
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private synchronized boolean ownsConnection(Socket expectedSocket) {
        return expectedSocket != null && socket == expectedSocket;
    }

    private void closeResourcesQuietly() {
        closeResourcesQuietly(null);
    }

    private synchronized void closeResourcesQuietly(Socket expectedSocket) {
        if (expectedSocket != null && socket != expectedSocket) {
            return;
        }
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

    private Throwable unwrapCompletionException(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void logRequestFailure(Throwable throwable) {
        if (isDisconnectedFailure(throwable)) {
            return;
        }
        LOGGER.warn("Request failed: {}", throwable.getMessage(), throwable);
    }

    private boolean isDisconnectedFailure(Throwable throwable) {
        if (!(throwable instanceof IllegalStateException) || throwable.getMessage() == null) {
            return false;
        }
        String message = throwable.getMessage();
        return CLIENT_NOT_CONNECTED_MESSAGE.equals(message)
                || "Disconnected from server.".equals(message)
                || "Network service is shutting down.".equals(message);
    }

    private String toUserFacingErrorMessage(Throwable throwable) {
        if (isDisconnectedFailure(throwable)) {
            return CLIENT_NOT_CONNECTED_MESSAGE;
        }
        if (throwable instanceof TimeoutException) {
            return "The server did not respond in time.";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "Network request failed." : message;
    }
}
