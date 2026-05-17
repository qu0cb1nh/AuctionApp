package net.auctionapp.server.messages;

import net.auctionapp.common.messages.Message;
import net.auctionapp.server.ClientHandler;

@FunctionalInterface
public interface MessageCommand<T extends Message> {
    void execute(T message, ClientHandler clientHandler);
}
