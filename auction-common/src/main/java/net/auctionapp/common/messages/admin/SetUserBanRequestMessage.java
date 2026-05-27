package net.auctionapp.common.messages.admin;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class SetUserBanRequestMessage extends Message {
    private String userId;
    private boolean banned;

    public SetUserBanRequestMessage() {
        super(MessageType.SET_USER_BAN_REQUEST);
    }

    public SetUserBanRequestMessage(String userId, boolean banned) {
        super(MessageType.SET_USER_BAN_REQUEST);
        this.userId = userId;
        this.banned = banned;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isBanned() {
        return banned;
    }
}
