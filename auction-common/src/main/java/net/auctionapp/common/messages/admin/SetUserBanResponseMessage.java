package net.auctionapp.common.messages.admin;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class SetUserBanResponseMessage extends Message {
    private String message;

    public SetUserBanResponseMessage() {
        super(MessageType.SET_USER_BAN_RESPONSE);
    }

    public SetUserBanResponseMessage(String message) {
        super(MessageType.SET_USER_BAN_RESPONSE);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
