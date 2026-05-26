package net.auctionapp.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.config.ServerConfig;
import net.auctionapp.server.dao.JdbcAuctionDao;
import net.auctionapp.server.dao.JdbcNotificationDao;
import net.auctionapp.server.dao.JdbcUserDao;
import net.auctionapp.server.dao.JdbcWatchListDao;
import net.auctionapp.server.services.AuthService;
import net.auctionapp.server.services.AuctionService;
import net.auctionapp.server.services.DatabaseService;
import net.auctionapp.server.services.NotificationService;
import net.auctionapp.server.services.UserService;
import net.auctionapp.server.services.WalletService;
import net.auctionapp.server.services.WatchListService;
import net.auctionapp.server.managers.SessionManager;
import net.auctionapp.server.messages.MessageRouter;
import net.auctionapp.server.exceptions.DatabaseException;
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
            DatabaseService.getInstance().createConnectionPool(); // Initializes database connection pool
            JdbcUserDao jdbcUserDao = new JdbcUserDao();
            JdbcAuctionDao jdbcAuctionDao = new JdbcAuctionDao();
            AuthService.getInstance().setUserDao(jdbcUserDao);
            AuctionService.getInstance().setAuctionDao(jdbcAuctionDao);
            WalletService.getInstance().setUserDao(jdbcUserDao);
            WalletService.getInstance().setAuctionDao(jdbcAuctionDao);
            NotificationService.getInstance().setNotificationDao(new JdbcNotificationDao());
            WatchListService.getInstance().setWatchListDao(new JdbcWatchListDao());
            AuctionService.getInstance().startStatusScheduler();
            MessageRouter messageRouter = new MessageRouter(
                    AuthService.getInstance(),
                    AuctionService.getInstance(),
                    UserService.getInstance(),
                    NotificationService.getInstance(),
                    WalletService.getInstance(),
                    WatchListService.getInstance()
            );

            String host = ServerConfig.getServerHost();
            int port = ServerConfig.getServerPort();
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(host, port), MAX_CLIENTS);
            LOGGER.info("Auction server is running on {}:{}", host, port);

            while (RUNNING.get()) {
                Socket clientSocket = serverSocket.accept(); // accept() method blocks until a connection is made to the socket
                if (CLIENTS.size() >= MAX_CLIENTS) {
                    rejectClient(clientSocket, "Maximum client limit reached.");
                    continue;
                }
                LOGGER.info("New client connected: {}", clientSocket.getInetAddress());

                // Each client will be handled by a separate thread
                ClientHandler handler = new ClientHandler(clientSocket, messageRouter);
                try {
                    CLIENT_THREAD_POOL.execute(handler);
                } catch (RejectedExecutionException e) {
                    LOGGER.warn("Rejected client {} due to overloaded worker pool.", clientSocket.getInetAddress());
                    rejectClient(clientSocket, "Server is busy. Try again later.");
                }
            }
        } catch (DatabaseException e) {
            LOGGER.error("Database initialization failed; server cannot start.", e);
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

    public static Set<ClientHandler> getConnectedClients() {
        return Set.copyOf(CLIENTS);
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
        AuctionService.getInstance().stopStatusScheduler();
        SessionManager.getInstance().clear();
        CLIENT_THREAD_POOL.shutdownNow();

        DatabaseService.getInstance().closeConnectionPool(); // Close database connection pool
        LOGGER.info("Server shutdown complete.");
    }

    private static void rejectClient(Socket clientSocket, String reason) {
        try {
            LOGGER.warn("Rejected client {}: {}", clientSocket.getInetAddress(), reason);
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            writer.println(JsonUtil.toJson(new ErrorResponseMessage(reason)));
        } catch (IOException e) {
            LOGGER.warn("Failed to send rejection message to client: {}", e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close rejected client socket: {}", e.getMessage());
            }
        }
    }
}
