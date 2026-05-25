package net.auctionapp.common.messages.admin;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AdminActionResponseMessage extends Message {
    private String message;

    public AdminActionResponseMessage() {
        super(MessageType.ADMIN_ACTION_SUCCESS);
    }

    public AdminActionResponseMessage(String message) {
        super(MessageType.ADMIN_ACTION_SUCCESS);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
