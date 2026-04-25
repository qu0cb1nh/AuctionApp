package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class PingMessage extends Message {
    public PingMessage() {
        super(MessageType.PING);
    }
}
