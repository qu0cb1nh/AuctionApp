package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class LoginResultMessage extends Message {
    private String userId;
    private String username;
    private String role;
    private String message;

    public LoginResultMessage() {
        super();
    }

    public LoginResultMessage(MessageType type, String userId, String username, String role, String message) {
        super(type);
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
