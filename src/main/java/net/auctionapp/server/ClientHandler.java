package net.auctionapp.server;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.utils.JsonUtil;
import net.auctionapp.server.controllers.AuctionController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final AuctionController auctionController;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.auctionController = AuctionController.getInstance();
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
                    // Convert JSON string to Message object
                    Message message = JsonUtil.fromJson(jsonString);

                    // TODO: Pass the message to the Controller for processing
                } catch (Exception e) {
                    System.err.println("Error processing JSON from client: " + e.getMessage());
                    // Optionally, send an error message back to the client
                }
            }
        } catch (IOException e) {
            System.out.println("A bidder at " + socket.getInetAddress() + " has disconnected.");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            ServerApp.clients.remove(this);
            System.out.println("Client removed. Current number of clients: " + ServerApp.clients.size());
        }
    }

    /**
     * Sends a message (already converted to JSON) to this client.
     * @param jsonMessage The message as a JSON string.
     */
    public void sendMessage(String jsonMessage) {
        if (out != null) {
            out.println(jsonMessage);
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
