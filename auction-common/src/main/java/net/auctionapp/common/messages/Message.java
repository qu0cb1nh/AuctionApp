package net.auctionapp.common.messages;

import java.util.UUID;

/**
 * Base class for all messages.
 * Contains a 'type' field so the Server and Client can determine
 * what to do with the received message.
 */
public class Message {
    private MessageType type;
    private String messageId;
    private String correlationId;

    // Default constructor is needed for Gson
    public Message() {}

    public Message(MessageType type) {
        this.type = type;
        this.messageId = UUID.randomUUID().toString();
    }

    public MessageType getType() {
        return type;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
