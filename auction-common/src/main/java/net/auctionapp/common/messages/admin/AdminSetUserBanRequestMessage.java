package net.auctionapp.common.messages.admin;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class AdminSetUserBanRequestMessage extends Message {
    private String userId;
    private boolean banned;

    public AdminSetUserBanRequestMessage() {
        super(MessageType.ADMIN_SET_USER_BAN_REQUEST);
    }

    public AdminSetUserBanRequestMessage(String userId, boolean banned) {
        super(MessageType.ADMIN_SET_USER_BAN_REQUEST);
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
