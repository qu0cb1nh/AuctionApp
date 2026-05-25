package net.auctionapp.common.messages.wallet;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class WithdrawRequestMessage extends Message {
    private BigDecimal amount;

    public WithdrawRequestMessage() {
        super(MessageType.WITHDRAW_REQUEST);
    }

    public WithdrawRequestMessage(BigDecimal amount) {
        super(MessageType.WITHDRAW_REQUEST);
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
