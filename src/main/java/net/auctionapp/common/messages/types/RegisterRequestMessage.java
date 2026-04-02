package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

/**
 * A message from the Client to the Server to register a new account.
 * Inherits from Message and has the type REGISTER_REQUEST.
 */
public class RegisterRequestMessage extends Message {
    private String username;
    private String password;

    // Default constructor is needed for Gson
    public RegisterRequestMessage() {
        super(MessageType.REGISTER_REQUEST);
    }

    public RegisterRequestMessage(String username, String password) {
        super(MessageType.REGISTER_REQUEST);
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
