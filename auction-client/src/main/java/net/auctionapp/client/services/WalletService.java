package net.auctionapp.client.services;

import net.auctionapp.client.messages.MessageListener;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.wallet.DepositRequestMessage;
import net.auctionapp.common.messages.wallet.GetWalletRequestMessage;
import net.auctionapp.common.messages.wallet.WithdrawRequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public final class WalletService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletService.class);
    private static final WalletService INSTANCE = new WalletService();

    public static WalletService getInstance() {
        return INSTANCE;
    }

    private WalletService() {
    }

    public void requestWallet(MessageListener<Message> callback) {
        LOGGER.debug("Requesting wallet balance.");
        NetworkService.getInstance().sendRequest(new GetWalletRequestMessage(), callback);
    }

    public void deposit(BigDecimal amount, MessageListener<Message> callback) {
        LOGGER.info("Submitting deposit request for amount {}.", amount);
        NetworkService.getInstance().sendRequest(new DepositRequestMessage(amount), callback);
    }

    public void withdraw(BigDecimal amount, MessageListener<Message> callback) {
        LOGGER.info("Submitting withdrawal request for amount {}.", amount);
        NetworkService.getInstance().sendRequest(new WithdrawRequestMessage(amount), callback);
    }
}
