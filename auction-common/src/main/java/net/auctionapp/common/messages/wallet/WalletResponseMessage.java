package net.auctionapp.common.messages.wallet;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class WalletResponseMessage extends Message {
    private BigDecimal balance;
    private BigDecimal pendingBalance;
    private String message;

    public WalletResponseMessage() {
        super(MessageType.WALLET_RESPONSE);
    }

    public WalletResponseMessage(MessageType type, BigDecimal balance, BigDecimal pendingBalance, String message) {
        super(type);
        this.balance = balance;
        this.pendingBalance = pendingBalance;
        this.message = message;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getPendingBalance() {
        return pendingBalance;
    }

    public void setPendingBalance(BigDecimal pendingBalance) {
        this.pendingBalance = pendingBalance;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
