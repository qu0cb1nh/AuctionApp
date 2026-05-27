package net.auctionapp.common.messages.admin;

import net.auctionapp.common.dto.AdminUserDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class GetUserListResponseMessage extends Message {
    private List<AdminUserDto> users;

    public GetUserListResponseMessage() {
        super(MessageType.GET_USER_LIST_RESPONSE);
    }

    public GetUserListResponseMessage(List<AdminUserDto> users) {
        super(MessageType.GET_USER_LIST_RESPONSE);
        this.users = users == null ? List.of() : List.copyOf(users);
    }

    public List<AdminUserDto> getUsers() {
        return users == null ? List.of() : List.copyOf(users);
    }
}
