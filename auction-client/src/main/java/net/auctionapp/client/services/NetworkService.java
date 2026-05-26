package net.auctionapp.client.services;

import javafx.application.Platform;
import net.auctionapp.client.exceptions.NetworkException;
import net.auctionapp.client.messages.MessageDispatcher;
import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.system.PingRequestMessage;
import net.auctionapp.common.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

public final class NetworkService {
    private static final NetworkService INSTANCE = new NetworkService();

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(3);

    private static final String NOT_CONNECTED = "Client is not connected.";
    private static final String DISCONNECTED = "Disconnected from server.";
    private static final String SHUTTING_DOWN = "Network service is shutting down.";

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkService.class);

    private final Map<String, CompletableFuture<Message>> pendingRequests = new ConcurrentHashMap<>();
    private final MessageDispatcher messageDispatcher = new MessageDispatcher();

    private volatile Connection connection;
    private ScheduledExecutorService heartbeatScheduler;

    private NetworkService() {
    }

    public static NetworkService getInstance() {
        return INSTANCE;
    }

    public synchronized void connect(String host, int port) throws NetworkException {
        if (isConnected()) {
            return;
        }

        closeConnection();

        try {
            Connection newConnection = Connection.open(host, port);
            connection = newConnection;

            Thread.ofVirtual()
                    .name("auction-client-listener")
                    .start(() -> listenForServerMessages(newConnection));

            startHeartbeat();
            LOGGER.info("Connected to server at {}:{}.", host, port);
        } catch (IOException e) {
            throw new NetworkException("Could not connect to " + host + ":" + port, e);
        }
    }

    public void sendMessage(Message message) {
        if (message == null) {
            LOGGER.error("Cannot send a null message.");
            return;
        }

        Connection current = connection;
        if (current == null || !current.isOpen()) {
            return;
        }

        if (!current.send(JsonUtil.toJson(message))) {
            markDisconnected(current);
        }
    }

    public void sendRequest(Message request, MessageListener<Message> callback) {
        Objects.requireNonNull(callback, "callback must not be null");

        sendRequest(request, DEFAULT_REQUEST_TIMEOUT).whenComplete((message, throwable) -> Platform.runLater(() -> {
            Message response = message;
            if (throwable != null) {
                String errorMessage = unwrap(throwable).getMessage();
                response = new ErrorResponseMessage(errorMessage == null ? "Network request failed." : errorMessage);
            }
            callback.onMessage(response);
        }));
    }

    public CompletableFuture<Message> sendRequest(Message request, Duration timeout) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Request cannot be null."));
        }

        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Timeout must be greater than zero."));
        }

        Connection current = connection;
        if (current == null || !current.isOpen()) {
            return CompletableFuture.failedFuture(new NetworkException(NOT_CONNECTED));
        }

        String requestId = prepareRequest(request);
        CompletableFuture<Message> future = new CompletableFuture<>();

        if (pendingRequests.putIfAbsent(requestId, future) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Duplicate request id: " + requestId));
        }

        if (!current.send(JsonUtil.toJson(request))) {
            pendingRequests.remove(requestId);
            markDisconnected(current);
            return CompletableFuture.failedFuture(new NetworkException(NOT_CONNECTED));
        }

        return withTimeout(future, timeout)
                .whenComplete((message, throwable) -> pendingRequests.remove(requestId));
    }

    public <T extends Message> void addMessageListener(MessageType type, MessageListener<T> listener) {
        messageDispatcher.addMessageListener(type, listener);
    }

    public void removeMessageListener(MessageType type, MessageListener<?> listener) {
        messageDispatcher.removeMessageListener(type, listener);
    }

    public boolean isConnected() {
        Connection current = connection;
        return current != null && current.isOpen();
    }

    public synchronized void shutdown() {
        stopHeartbeat();
        failPendingRequests(SHUTTING_DOWN);
        closeConnection();
        LOGGER.info("Network service shut down.");
    }

    private void listenForServerMessages(Connection listenedConnection) {
        try {
            String json;
            while ((json = listenedConnection.readLine()) != null) {
                try {
                    handleMessage(JsonUtil.fromJson(json));
                } catch (Exception e) {
                    LOGGER.warn("Received malformed message from server: {}", json, e);
                }
            }
        } catch (IOException e) {
            if (!"Socket closed".equalsIgnoreCase(e.getMessage())) {
                LOGGER.warn("Disconnected from server.", e);
            }
        } finally {
            markDisconnected(listenedConnection);
        }
    }

    private void handleMessage(Message message) {
        if (message == null) {
            return;
        }
        String correlationId = message.getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            CompletableFuture<Message> pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.complete(message);
            }
            return;
        }
        messageDispatcher.dispatch(message);
    }

    private synchronized void startHeartbeat() {
        stopHeartbeat();

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auction-client-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            Connection current = connection;
            if (current == null || !current.isOpen()) {
                return;
            }

            sendRequest(new PingRequestMessage(), HEARTBEAT_TIMEOUT)
                    .thenAccept(response -> {
                        if (response == null || response.getType() != MessageType.PONG) {
                            markDisconnected(current);
                        }
                    })
                    .exceptionally(throwable -> {
                        markDisconnected(current);
                        return null;
                    });
        }, HEARTBEAT_INTERVAL.toSeconds(), HEARTBEAT_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    private synchronized void stopHeartbeat() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }
    }

    private synchronized void markDisconnected(Connection expected) {
        if (connection != expected) {
            return;
        }
        stopHeartbeat();
        failPendingRequests(DISCONNECTED);
        closeConnection();
        LOGGER.info(DISCONNECTED);
    }

    private synchronized void closeConnection() {
        Connection current = connection;
        if (current == null) {
            return;
        }
        connection = null;
        current.close();
    }

    private void failPendingRequests(String reason) {
        NetworkException exception = new NetworkException(reason);
        pendingRequests.values().forEach(future -> future.completeExceptionally(exception));
        pendingRequests.clear();
    }

    private String prepareRequest(Message request) {
        String requestId = request.getMessageId();

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
            request.setMessageId(requestId);
        }

        request.setCorrelationId(null);
        return requestId;
    }

    private CompletableFuture<Message> withTimeout(CompletableFuture<Message> future, Duration timeout) {
        return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionallyCompose(throwable -> {
                    Throwable failure = unwrap(throwable);

                    if (failure instanceof TimeoutException) {
                        return CompletableFuture.failedFuture(
                                new NetworkException("The server did not respond in time.", failure)
                        );
                    }

                    return CompletableFuture.failedFuture(failure);
                });
    }

    private Throwable unwrap(Throwable throwable) {
        while ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
                && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        return throwable;
    }

    private record Connection(Socket socket, BufferedReader reader, PrintWriter writer) {
        static Connection open(String host, int port) throws IOException {
            Socket socket = new Socket();

            try {
                socket.connect(new InetSocketAddress(host, port), (int) CONNECT_TIMEOUT.toMillis());
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                return new Connection(socket, reader, writer);
            } catch (IOException | RuntimeException e) {
                socket.close();
                throw e;
            }
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        synchronized boolean send(String json) {
            writer.println(json);
            return !writer.checkError();
        }

        boolean isOpen() {
            return socket.isConnected() && !socket.isClosed();
        }

        void close() {
            writer.close();
            try {
                reader.close();
            } catch (IOException e) {
                LOGGER.debug("Failed to close reader.", e);
            }
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.debug("Failed to close socket.", e);
            }
        }
    }
}