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
import net.auctionapp.server.dao.JdbcUserDao;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.DatabaseManager;
import net.auctionapp.server.managers.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerApp {
    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    // Thread-safe set for all connected clients.
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static ServerSocket serverSocket;

    private static final ExecutorService clientThreadPool = Executors.newCachedThreadPool(); // Creates a thread pool for client handlers

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerApp::shutdown)); // Shutdown on JVM exit (Ctrl+C, IDE stop, etc.)

        try {
            DatabaseManager.getInstance().createConnectionPool(); // Initializes database connection pool
            AuthManager.getInstance().setUserDao(new JdbcUserDao());

            serverSocket = new ServerSocket(ConfigUtil.getServerPort());
            logger.info("Auction server is running on port: {}", ConfigUtil.getServerPort());

            while (running.get()) {
                Socket clientSocket = serverSocket.accept(); // accept() method blocks until a connection is made to the socket
                logger.info("New client connected: {}", clientSocket.getInetAddress());

                // Each client will be handled by a separate thread
                ClientHandler handler = new ClientHandler(clientSocket);
                registerClient(handler);
                clientThreadPool.execute(handler);
            }
        } catch (SocketException e) {
            if (running.get()) {
                logger.error("Server socket error: {}", e.getMessage());
            } else {
                logger.info("Server socket closed as part of shutdown.");
            }
        } catch (IOException e) {
            logger.error("An I/O error occurred in the main server loop", e);
        } finally {
            shutdown();
        }
    }

    public static void registerClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
        logger.info("Client registered. Current number of clients: {}", clients.size());
    }

    public static void unregisterClient(ClientHandler clientHandler) {
        if (clients.remove(clientHandler)) {
            logger.info("Client unregistered. Current number of clients: {}", clients.size());
        }
    }

    public static void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        logger.info("Server shutdown initiated...");

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close(); // close the server socket
            } catch (IOException e) {
                logger.error("Failed to close server socket", e);
            }
        }

        for (ClientHandler client : clients) {
            client.closeConnection(); // disconnect all clients
        }
        clients.clear(); // Clear the client set (again, to be safe)
        SessionManager.getInstance().clear();

        DatabaseManager.getInstance().closeConnectionPool(); // Close database connection pool
        logger.info("Server shutdown complete.");
    }

    // Method to broadcast a new price message to ALL clients (Realtime Update)
    public static void broadcast(String message) {
        logger.debug("Broadcasting message to {} clients: {}", clients.size(), message);
        for (ClientHandler client : clients) {
            if (!client.sendMessage(message)) {
                // sendMessage already closes and unregisters this client.
                logger.warn("Skipped a disconnected client during broadcast.");
            }
        }
    }
}
