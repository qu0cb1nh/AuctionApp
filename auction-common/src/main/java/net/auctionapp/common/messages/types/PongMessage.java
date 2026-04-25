package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class PongMessage extends Message {
    public PongMessage() {
        super(MessageType.PONG);
    }
}
