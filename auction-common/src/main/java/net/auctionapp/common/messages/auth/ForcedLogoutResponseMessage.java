package net.auctionapp.common.messages.auth;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class ForcedLogoutResponseMessage extends Message {
    private String reason;

    public ForcedLogoutResponseMessage() {
        super(MessageType.FORCED_LOGOUT);
    }

    public ForcedLogoutResponseMessage(String reason) {
        super(MessageType.FORCED_LOGOUT);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
