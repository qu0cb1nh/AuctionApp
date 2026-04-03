package net.auctionapp.client;

import javafx.application.Platform;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.utils.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class NetworkService {
    private final Map<MessageType, List<Consumer<Message>>> messageHandlers = new ConcurrentHashMap<>();

    private PrintWriter out;
    private Socket socket;
    private BufferedReader in;

    public void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Thread listenerThread = new Thread(this::listenForServerMessages);
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (IOException e) {
            System.err.println("Cannot connect to server: " + e.getMessage());
        }
    }

    public void sendMessage(Message message) {
        if (message == null) {
            System.err.println("Cannot send a null message.");
            return;
        }
        if (out == null) {
            System.err.println("Cannot send message: client is not connected.");
            return;
        }

        out.println(JsonUtil.toJson(message));
        if (out.checkError()) {
            System.err.println("Failed to send message to server.");
        }
    }

    public void addMessageHandler(MessageType type, Consumer<Message> handler) {
        if (type == null || handler == null) {
            return;
        }

        messageHandlers
                .computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(handler);
    }

    public void removeMessageHandler(MessageType type, Consumer<Message> handler) {
        if (type == null || handler == null) {
            return;
        }

        List<Consumer<Message>> handlers = messageHandlers.get(type);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    public void shutdown() {
        if (socket == null || socket.isClosed()) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("Failed to close client socket: " + e.getMessage());
        }
    }

    private void listenForServerMessages() {
        try {
            String jsonString;
            while ((jsonString = in.readLine()) != null) {
                final Message message = JsonUtil.fromJson(jsonString);
                Platform.runLater(() -> routeMessageToHandlers(message));
            }
        } catch (IOException e) {
            System.err.println("Disconnected from server: " + e.getMessage());
        }
    }

    private void routeMessageToHandlers(Message message) {
        if (message == null) {
            return;
        }

        List<Consumer<Message>> handlers = messageHandlers.get(message.getType());
        if (handlers == null) {
            return;
        }

        for (Consumer<Message> handler : handlers) {
            handler.accept(message);
        }
    }
}
