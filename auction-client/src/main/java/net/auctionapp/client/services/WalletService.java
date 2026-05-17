package net.auctionapp.client.services;

import net.auctionapp.client.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.DepositRequestMessage;
import net.auctionapp.common.messages.types.WithdrawRequestMessage;

import java.math.BigDecimal;

public final class WalletService {
    private static final WalletService INSTANCE = new WalletService();

    public static WalletService getInstance() {
        return INSTANCE;
    }

    private WalletService() {
    }

    public void deposit(BigDecimal amount, MessageListener<Message> callback) {
        DepositRequestMessage request = new DepositRequestMessage(amount);
        NetworkService.getInstance().sendRequest(request, callback);
    }

    public void withdraw(BigDecimal amount, MessageListener<Message> callback) {
        WithdrawRequestMessage request = new WithdrawRequestMessage(amount);
        NetworkService.getInstance().sendRequest(request, callback);
    }
}
