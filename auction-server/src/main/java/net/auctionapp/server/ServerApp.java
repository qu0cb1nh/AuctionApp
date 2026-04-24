package net.auctionapp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.auctionapp.common.utils.ConfigUtil;
import net.auctionapp.server.dao.JdbcAuctionDao;
import net.auctionapp.server.dao.JdbcUserDao;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.AuctionManager;
import net.auctionapp.server.managers.DatabaseManager;
import net.auctionapp.server.managers.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApp.class);
    // Thread-safe set for all connected clients.
    private static final Set<ClientHandler> CLIENTS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    private static ServerSocket serverSocket;

    private static final ExecutorService CLIENT_THREAD_POOL = Executors.newCachedThreadPool(); // Creates a thread pool for client handlers

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerApp::shutdown)); // Shutdown on JVM exit (Ctrl+C, IDE stop, etc.)

        try {
            DatabaseManager.getInstance().createConnectionPool(); // Initializes database connection pool
            AuthManager.getInstance().setUserDao(new JdbcUserDao());
            AuctionManager.getInstance().setAuctionDao(new JdbcAuctionDao());

            serverSocket = new ServerSocket(ConfigUtil.getServerPort());
            LOGGER.info("Auction server is running on port: {}", ConfigUtil.getServerPort());

            while (RUNNING.get()) {
                Socket clientSocket = serverSocket.accept(); // accept() method blocks until a connection is made to the socket
                LOGGER.info("New client connected: {}", clientSocket.getInetAddress());

                // Each client will be handled by a separate thread
                ClientHandler handler = new ClientHandler(clientSocket);
                registerClient(handler);
                CLIENT_THREAD_POOL.execute(handler);
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

        DatabaseManager.getInstance().closeConnectionPool(); // Close database connection pool
        LOGGER.info("Server shutdown complete.");
    }

    // Method to broadcast a new price message to ALL clients (Realtime Update)
    public static void broadcast(String message) {
        LOGGER.debug("Broadcasting message to {} clients: {}", CLIENTS.size(), message);
        for (ClientHandler client : CLIENTS) {
            if (!client.sendMessage(message)) {
                // sendMessage already closes and unregisters this client.
                LOGGER.warn("Skipped a disconnected client during broadcast.");
            }
        }
    }
}
