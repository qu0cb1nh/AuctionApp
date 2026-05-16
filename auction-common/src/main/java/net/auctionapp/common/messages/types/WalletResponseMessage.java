package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class WalletResponseMessage extends Message {
    private BigDecimal balance;
    private String message;

    public WalletResponseMessage() {
        super(MessageType.WALLET_RESPONSE);
    }

    public WalletResponseMessage(MessageType type, BigDecimal balance, String message) {
        super(type);
        this.balance = balance;
        this.message = message;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
