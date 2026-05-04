package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.models.users.UserRole;

public class LoginResultMessage extends Message {
    private String userId;
    private String username;
    private UserRole role;
    private String message;

    public LoginResultMessage() {
        super();
    }

    public LoginResultMessage(MessageType type, String userId, String username, UserRole role, String message) {
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

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
