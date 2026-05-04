package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;

@FunctionalInterface
public interface MessageListener<T extends Message> {
    void onMessage(T message);
}

