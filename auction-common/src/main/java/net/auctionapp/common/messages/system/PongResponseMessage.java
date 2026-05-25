package net.auctionapp.common.messages.system;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class PongResponseMessage extends Message {
    public PongResponseMessage() {
        super(MessageType.PONG);
    }
}
