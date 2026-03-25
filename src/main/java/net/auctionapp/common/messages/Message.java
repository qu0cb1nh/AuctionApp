package net.auctionapp.common.messages;

/**
 * Base class for all messages.
 * Contains a 'type' field so the Server and Client can determine
 * what to do with the received message.
 */
public class Message {
    private MessageType type;

    // Default constructor is needed for Gson
    public Message() {}

    public Message(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }
}
