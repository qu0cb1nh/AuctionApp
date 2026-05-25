package net.auctionapp.common.messages.admin;

import net.auctionapp.common.dto.AdminUserView;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class AdminGetUsersResponseMessage extends Message {
    private List<AdminUserView> users;

    public AdminGetUsersResponseMessage() {
        super(MessageType.ADMIN_GET_USERS_RESPONSE);
    }

    public AdminGetUsersResponseMessage(List<AdminUserView> users) {
        super(MessageType.ADMIN_GET_USERS_RESPONSE);
        this.users = users == null ? List.of() : List.copyOf(users);
    }

    public List<AdminUserView> getUsers() {
        return users == null ? List.of() : List.copyOf(users);
    }
}
