package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.wallet.DepositRequestMessage;
import net.auctionapp.common.messages.wallet.GetWalletRequestMessage;
import net.auctionapp.common.messages.wallet.WithdrawRequestMessage;

import java.math.BigDecimal;

public final class WalletService {
    private static final WalletService INSTANCE = new WalletService();

    public static WalletService getInstance() {
        return INSTANCE;
    }

    private WalletService() {
    }

    public void requestWallet(MessageListener<Message> callback) {
        NetworkService.getInstance().sendRequest(new GetWalletRequestMessage(), callback);
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
