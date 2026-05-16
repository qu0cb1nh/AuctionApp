package net.auctionapp.common.messages;

@FunctionalInterface
public interface MessageListener<T extends Message> {
    void onMessage(T message);
}

