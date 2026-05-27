package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.system.PongResponseMessage;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.config.ServerConfig;
import net.auctionapp.server.dao.JdbcAuctionDao;
import net.auctionapp.server.dao.JdbcBalanceDao;
import net.auctionapp.server.dao.JdbcNotificationDao;
import net.auctionapp.server.dao.JdbcUserDao;
import net.auctionapp.server.dao.JdbcWatchListDao;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.managers.SessionManager;
import net.auctionapp.server.messages.MessageRouter;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.AuctionManager;
import net.auctionapp.server.database.DatabaseConnection;
import net.auctionapp.server.managers.NotificationManager;
import net.auctionapp.server.managers.UserManager;
import net.auctionapp.server.managers.WalletManager;
import net.auctionapp.server.managers.WatchListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApp.class);
    private static final int MAX_CLIENTS = 200;

    private static final Set<ClientHandler> CLIENTS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    private static final ExecutorService CLIENT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerApp::shutdown));

        try {
            DatabaseConnection.getInstance().createConnectionPool();

            JdbcUserDao jdbcUserDao = new JdbcUserDao();
            JdbcBalanceDao jdbcBalanceDao = new JdbcBalanceDao();
            JdbcAuctionDao jdbcAuctionDao = new JdbcAuctionDao(
                    DatabaseConnection.getInstance(),
                    jdbcBalanceDao,
                    jdbcUserDao
            );

            AuthManager.getInstance().setUserDao(jdbcUserDao);
            AuctionManager.getInstance().setAuctionDao(jdbcAuctionDao);
            WalletManager.getInstance().setBalanceDao(jdbcBalanceDao);
            WalletManager.getInstance().setAuctionDao(jdbcAuctionDao);
            NotificationManager.getInstance().setNotificationDao(new JdbcNotificationDao());
            WatchListManager.getInstance().setWatchListDao(new JdbcWatchListDao());

            AuctionManager.getInstance().startStatusScheduler();

            MessageRouter messageRouter = new MessageRouter();

            messageRouter.register(MessageType.PING, Message.class,
                    (message, handler) -> handler.sendResponse(new PongResponseMessage(), message));
            AuthManager.getInstance().registerCommands(messageRouter);
            AuctionManager.getInstance().registerCommands(messageRouter);
            UserManager.getInstance().registerCommands(messageRouter);
            NotificationManager.getInstance().registerCommands(messageRouter);
            WalletManager.getInstance().registerCommands(messageRouter);
            WatchListManager.getInstance().registerCommands(messageRouter);

            String host = ServerConfig.getServerHost();
            int port = ServerConfig.getServerPort();

            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(host, port), MAX_CLIENTS);

            LOGGER.info("Auction server is running on {}:{}", host, port);

            while (RUNNING.get()) {
                Socket clientSocket = serverSocket.accept();

                if (CLIENTS.size() >= MAX_CLIENTS) {
                    rejectClient(clientSocket, "Maximum client limit reached.");
                    continue;
                }

                LOGGER.info("New client connected: {}", clientSocket.getInetAddress());

                try {
                    CLIENT_EXECUTOR.execute(new ClientHandler(clientSocket, messageRouter));
                } catch (RejectedExecutionException e) {
                    LOGGER.warn("Rejected client {} because server is shutting down or overloaded.",
                            clientSocket.getInetAddress());
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
            LOGGER.error("An I/O error occurred in the main server loop.", e);
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

        closeServerSocket();

        for (ClientHandler client : CLIENTS) {
            client.closeConnection();
        }

        CLIENTS.clear();
        AuctionManager.getInstance().stopStatusScheduler();
        SessionManager.getInstance().clear();
        CLIENT_EXECUTOR.shutdownNow();
        DatabaseConnection.getInstance().closeConnectionPool();

        LOGGER.info("Server shutdown complete.");
    }

    private static void closeServerSocket() {
        if (serverSocket == null || serverSocket.isClosed()) {
            return;
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.error("Failed to close server socket.", e);
        }
    }

    private static void rejectClient(Socket clientSocket, String reason) {
        try (clientSocket;
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            LOGGER.warn("Rejected client {}: {}", clientSocket.getInetAddress(), reason);
            writer.println(JsonUtil.toJson(new ErrorResponseMessage(reason)));
        } catch (IOException e) {
            LOGGER.warn("Failed to reject client cleanly: {}", e.getMessage());
        }
    }
}
