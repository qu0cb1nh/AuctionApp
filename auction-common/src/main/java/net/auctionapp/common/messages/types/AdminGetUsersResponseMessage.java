package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class AdminGetUsersResponseMessage extends Message {
    private List<AdminUserViewMessage> users;

    public AdminGetUsersResponseMessage() {
        super(MessageType.ADMIN_GET_USERS_RESPONSE);
    }

    public AdminGetUsersResponseMessage(List<AdminUserViewMessage> users) {
        super(MessageType.ADMIN_GET_USERS_RESPONSE);
        this.users = users == null ? List.of() : List.copyOf(users);
    }

    public List<AdminUserViewMessage> getUsers() {
        return users == null ? List.of() : List.copyOf(users);
    }
}
