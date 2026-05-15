package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.math.BigDecimal;

public class DepositRequestMessage extends Message {
    private BigDecimal amount;

    public DepositRequestMessage() {
        super(MessageType.DEPOSIT_REQUEST);
    }

    public DepositRequestMessage(BigDecimal amount) {
        super(MessageType.DEPOSIT_REQUEST);
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
