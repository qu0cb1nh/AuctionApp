package net.auctionapp.client;

import net.auctionapp.common.messages.Message;

@FunctionalInterface
public interface MessageListener<T extends Message> {
    void onMessage(T message);
}

