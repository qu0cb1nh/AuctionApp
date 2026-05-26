package net.auctionapp.client.messages;

import javafx.application.Platform;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MessageDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatcher.class);

    private final Map<MessageType, List<MessageListener<? extends Message>>> messageListeners = new ConcurrentHashMap<>();

    public <T extends Message> void addMessageListener(MessageType type, MessageListener<T> listener) {
        if (type == null || listener == null) {
            return;
        }
        messageListeners
                .computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    public void removeMessageListener(MessageType type, MessageListener<?> listener) {
        if (type == null || listener == null) {
            return;
        }
        List<MessageListener<? extends Message>> listeners = messageListeners.get(type);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void dispatch(Message message) {
        if (message == null || message.getType() == null) {
            LOGGER.warn("Cannot dispatch a message without a type.");
            return;
        }
        List<MessageListener<? extends Message>> listeners = messageListeners.get(message.getType());
        if (listeners == null) {
            return;
        }
        // Message listeners run on the JavaFX Application Thread and should not do heavy work.
        Platform.runLater(() -> dispatchToListeners(message, listeners));
    }

    @SuppressWarnings("unchecked")
    private void dispatchToListeners(Message message, List<MessageListener<? extends Message>> listeners) {
        for (MessageListener<? extends Message> listener : listeners) {
            try {
                ((MessageListener<Message>) listener).onMessage(message);
            } catch (Exception e) {
                LOGGER.warn("Listener failed while handling {} message.", message.getType(), e);
            }
        }
    }
}
