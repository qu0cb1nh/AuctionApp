package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AdminActionResultMessage extends Message {
    private String message;

    public AdminActionResultMessage() {
        super(MessageType.ADMIN_ACTION_SUCCESS);
    }

    public AdminActionResultMessage(String message) {
        super(MessageType.ADMIN_ACTION_SUCCESS);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
