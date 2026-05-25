package net.auctionapp.common.messages.system;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class PingRequestMessage extends Message {
    public PingRequestMessage() {
        super(MessageType.PING);
    }
}
