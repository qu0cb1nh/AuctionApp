package net.auctionapp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.auctionapp.common.utils.ConfigUtil;
import net.auctionapp.server.dao.JdbcAuctionDao;
import net.auctionapp.server.dao.JdbcNotificationDao;
import net.auctionapp.server.dao.JdbcUserDao;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.AuctionManager;
import net.auctionapp.server.managers.DatabaseManager;
import net.auctionapp.server.managers.NotificationManager;
import net.auctionapp.server.managers.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApp.class);
    private static final int MAX_CLIENTS = 200;
    private static final int CLIENT_THREAD_POOL_SIZE = 64;
    private static final int CLIENT_TASK_QUEUE_SIZE = 256;
    // Thread-safe set for all connected clients.
    private static final Set<ClientHandler> CLIENTS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    private static ServerSocket serverSocket;

    private static final ExecutorService CLIENT_THREAD_POOL = new ThreadPoolExecutor(
            CLIENT_THREAD_POOL_SIZE,
            CLIENT_THREAD_POOL_SIZE,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(CLIENT_TASK_QUEUE_SIZE)
    );

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerApp::shutdown)); // Shutdown on JVM exit (Ctrl+C, IDE stop, etc.)

        try {
            DatabaseManager.getInstance().createConnectionPool(); // Initializes database connection pool
            AuthManager.getInstance().setUserDao(new JdbcUserDao());
            AuctionManager.getInstance().setAuctionDao(new JdbcAuctionDao());
            NotificationManager.getInstance().setNotificationDao(new JdbcNotificationDao());

            serverSocket = new ServerSocket(ConfigUtil.getServerPort(), MAX_CLIENTS);
            LOGGER.info("Auction server is running on port: {}", ConfigUtil.getServerPort());

            while (RUNNING.get()) {
                Socket clientSocket = serverSocket.accept(); // accept() method blocks until a connection is made to the socket
                if (CLIENTS.size() >= MAX_CLIENTS) {
                    rejectClient(clientSocket, "Maximum client limit reached.");
                    continue;
                }
                LOGGER.info("New client connected: {}", clientSocket.getInetAddress());

                // Each client will be handled by a separate thread
                ClientHandler handler = new ClientHandler(clientSocket);
                registerClient(handler);
                try {
                    CLIENT_THREAD_POOL.execute(handler);
                } catch (RejectedExecutionException e) {
                    LOGGER.warn("Rejected client {} due to overloaded worker pool.", clientSocket.getInetAddress());
                    handler.closeConnection();
                }
            }
        } catch (SocketException e) {
            if (RUNNING.get()) {
                LOGGER.error("Server socket error: {}", e.getMessage());
            } else {
                LOGGER.info("Server socket closed as part of shutdown.");
            }
        } catch (IOException e) {
            LOGGER.error("An I/O error occurred in the main server loop", e);
        } finally {
            shutdown();
        }
    }

    public static void registerClient(ClientHandler clientHandler) {
        CLIENTS.add(clientHandler);
        LOGGER.info("Client registered. Current number of clients: {}", CLIENTS.size());
    }

    public static void unregisterClient(ClientHandler clientHandler) {
        if (CLIENTS.remove(clientHandler)) {
            LOGGER.info("Client unregistered. Current number of clients: {}", CLIENTS.size());
        }
    }

    public static void shutdown() {
        if (!RUNNING.compareAndSet(true, false)) {
            return;
        }
        LOGGER.info("Server shutdown initiated...");

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close(); // close the server socket
            } catch (IOException e) {
                LOGGER.error("Failed to close server socket", e);
            }
        }

        for (ClientHandler client : CLIENTS) {
            client.closeConnection(); // disconnect all clients
        }
        CLIENTS.clear(); // Clear the client set (again, to be safe)
        SessionManager.getInstance().clear();
        CLIENT_THREAD_POOL.shutdownNow();

        DatabaseManager.getInstance().closeConnectionPool(); // Close database connection pool
        LOGGER.info("Server shutdown complete.");
    }

    // Broadcast a message to ALL clients
    public static void broadcast(String message) {
        LOGGER.debug("Broadcasting message to {} clients: {}", CLIENTS.size(), message);
        for (ClientHandler client : CLIENTS) {
            if (!client.sendMessage(message)) {
                // sendMessage already closes and unregisters this client.
                LOGGER.warn("Skipped a disconnected client during broadcast.");
            }
        }
    }

    private static void rejectClient(Socket clientSocket, String reason) {
        try {
            LOGGER.warn("Rejected client {}: {}", clientSocket.getInetAddress(), reason);
            clientSocket.close();
        } catch (IOException e) {
            LOGGER.warn("Failed to close rejected client socket: {}", e.getMessage());
        }
    }
}
