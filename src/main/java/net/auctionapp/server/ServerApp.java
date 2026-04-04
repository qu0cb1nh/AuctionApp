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
import net.auctionapp.server.database.DatabaseManager;

public class ServerApp {

    // Thread-safe set for all connected clients.
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static ServerSocket serverSocket;

    private static final ExecutorService clientThreadPool = Executors.newCachedThreadPool(); // Creates a thread pool for client handlers

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerApp::shutdown)); // Shutdown on JVM exit (Ctrl+C, IDE stop, etc.)

        try {
            DatabaseManager.getInstance().createConnectionPool(); // Initializes database connection pool

            serverSocket = new ServerSocket(ConfigUtil.getServerPort());
            System.out.println("Auction server is running on port: " + ConfigUtil.getServerPort());

            while (running.get()) {
                Socket clientSocket = serverSocket.accept(); // accept() method blocks until a connection is made to the socket
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Each client will be handled by a separate thread
                ClientHandler handler = new ClientHandler(clientSocket);
                registerClient(handler);
                clientThreadPool.execute(handler);
            }
        } catch (SocketException e) {
            if (running.get()) {
                System.err.println("Server socket error: " + e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    public static void registerClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    public static void unregisterClient(ClientHandler clientHandler) {
        if (clients.remove(clientHandler)) {
            System.out.println("Client removed. Current number of clients: " + clients.size());
        }
    }

    public static void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close(); // close the server socket
            } catch (IOException e) {
                System.err.println("Failed to close server socket: " + e.getMessage());
            }
        }

        for (ClientHandler client : clients) {
            client.closeConnection(); // disconnect all clients
        }
        clients.clear(); // Clear the client set (again, to be safe)

        DatabaseManager.getInstance().closeConnectionPool(); // Close database connection pool
        System.out.println("Server shutdown complete.");
    }

    // Method to broadcast a new price message to ALL clients (Realtime Update)
    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            if (!client.sendMessage(message)) {
                // sendMessage already closes and unregisters this client.
                System.out.println("Skipped disconnected client during broadcast.");
            }
        }
    }
}
