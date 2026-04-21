package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class LoginRequestMessage extends Message {
    private String username;
    private String password;

    public LoginRequestMessage() {
        super(MessageType.LOGIN_REQUEST);
    }

    public LoginRequestMessage(String username, String password) {
        super(MessageType.LOGIN_REQUEST);
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

