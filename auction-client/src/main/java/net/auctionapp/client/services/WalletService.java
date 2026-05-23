package net.auctionapp.client.services;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.DepositRequestMessage;
import net.auctionapp.common.messages.types.GetWalletRequestMessage;
import net.auctionapp.common.messages.types.WithdrawRequestMessage;

import java.math.BigDecimal;
import java.util.function.Consumer;

public final class WalletService {
    private static final WalletService INSTANCE = new WalletService();

    public static WalletService getInstance() {
        return INSTANCE;
    }

    private WalletService() {
    }

    public void requestWallet(MessageListener<Message> callback, Consumer<Throwable> onError) {
        NetworkService.getInstance().sendRequest(new GetWalletRequestMessage(), callback, onError);
    }

    public void deposit(BigDecimal amount, MessageListener<Message> callback, Consumer<Throwable> onError) {
        DepositRequestMessage request = new DepositRequestMessage(amount);
        NetworkService.getInstance().sendRequest(request, callback, onError);
    }

    public void withdraw(BigDecimal amount, MessageListener<Message> callback, Consumer<Throwable> onError) {
        WithdrawRequestMessage request = new WithdrawRequestMessage(amount);
        NetworkService.getInstance().sendRequest(request, callback, onError);
    }
}
