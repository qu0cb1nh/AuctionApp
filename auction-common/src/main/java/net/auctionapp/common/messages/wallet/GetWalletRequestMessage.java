package net.auctionapp.common.messages.wallet;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class GetWalletRequestMessage extends Message {
    public GetWalletRequestMessage() {
        super(MessageType.GET_WALLET_REQUEST);
    }
}
