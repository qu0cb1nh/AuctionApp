package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

/**
 * A message from the Server to the Client to report registration results.
 * Inherits from Message and has the type REGISTER_SUCCESS or REGISTER_FAILURE.
 */
public class RegisterResultMessage extends Message {
    private String username;
    private String message;

    // Default constructor is needed for Gson
    public RegisterResultMessage() {
        super();
    }

    public RegisterResultMessage(MessageType type, String username, String message) {
        super(type);
        this.username = username;
        this.message = message;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
