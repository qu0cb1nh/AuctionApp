package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.controllers.AuthController;
import net.auctionapp.server.controllers.AuctionController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final AuctionController auctionController;
    private final AuthController authController;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.auctionController = AuctionController.getInstance();
        this.authController = AuthController.getInstance();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String jsonString;
            while ((jsonString = in.readLine()) != null) {
                System.out.println("Received JSON from client " + socket.getInetAddress() + ": " + jsonString);

                try {
                    Message message = JsonUtil.fromJson(jsonString);
                    if (message == null) {
                        continue;
                    }

                    handleMessagesFromClient(message);
                } catch (Exception e) {
                    System.err.println("Error processing JSON from client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("A bidder at " + socket.getInetAddress() + " has disconnected.");
        } finally {
            closeConnection();
        }
    }

    public void closeConnection() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            socket.close();
        } catch (IOException e) {
            // Ignore close errors during disconnect/shutdown.
        } finally {
            ServerApp.unregisterClient(this);
        }
    }

    /**
     * Sends a message (already converted to JSON) to this client.
     *
     * @param jsonMessage The message as a JSON string.
     * @return true if the write succeeds, false if this client is not writable.
     */
    public boolean sendMessage(String jsonMessage) {
        if (closed.get() || out == null || socket.isClosed()) {
            closeConnection();
            return false;
        }

        out.println(jsonMessage);
        if (out.checkError()) {
            closeConnection();
            return false;
        }

        return true;
    }

    private void handleMessagesFromClient(Message message) {
        switch (message.getType()) {
            case LOGIN_REQUEST:
                authController.handleLogin((LoginRequestMessage) message, this);
                break;
            case REGISTER_REQUEST:
                authController.handleRegister((RegisterRequestMessage) message, this);
                break;
            default:
                //auctionController.processMessage(message, this);
                break;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientHandler that = (ClientHandler) o;
        return socket.equals(that.socket);
    }

    @Override
    public int hashCode() {
        return socket.hashCode();
    }
}
