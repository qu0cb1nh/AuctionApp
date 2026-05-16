package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.DepositRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetWalletRequestMessage;
import net.auctionapp.common.messages.types.WithdrawRequestMessage;
import net.auctionapp.common.messages.types.WalletResponseMessage;
import net.auctionapp.common.models.users.User;
import net.auctionapp.common.utils.MoneyUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.exceptions.AuctionAppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public final class BalanceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceManager.class);
    private static final BalanceManager INSTANCE = new BalanceManager();

    private final AuthManager authManager = AuthManager.getInstance();

    private BalanceManager() {
    }

    public static BalanceManager getInstance() {
        return INSTANCE;
    }

    public void handleGetWallet(GetWalletRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            User user = authManager.requireActiveUserById(clientHandler.getAuthenticatedId());
            WalletResponseMessage response = new WalletResponseMessage(
                    MessageType.WALLET_RESPONSE,
                    user.getBalance(),
                    "Wallet loaded."
            );
            clientHandler.sendResponse(response, request);
        } catch (AuctionAppException e) {
            ErrorMessage error = new ErrorMessage(e.getMessage());
            clientHandler.sendResponse(error, request);
            LOGGER.warn("Wallet load failed: {}", e.getMessage());
        } catch (Exception e) {
            ErrorMessage error = new ErrorMessage("An error occurred while loading wallet.");
            clientHandler.sendResponse(error, request);
            LOGGER.error("Error processing wallet request.", e);
        }
    }

    public void handleDeposit(DepositRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            String userId = clientHandler.getAuthenticatedId();

            BigDecimal amount = request.getAmount();
            MoneyUtil.requirePositiveMoney(amount, "Deposit amount");

            User user = authManager.deposit(userId, amount);
            WalletResponseMessage response = new WalletResponseMessage(
                    MessageType.WALLET_RESPONSE,
                    user.getBalance(),
                    "Deposit successful."
            );
            clientHandler.sendResponse(response, request);
            LOGGER.info("User '{}' deposited ${}.", user.getUsername(), amount);

        } catch (AuctionAppException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Deposit failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Deposit failed: {}", e.getMessage());
        } catch (Exception e) {
            sendError(clientHandler, request, "An error occurred during deposit.");
            LOGGER.error("Error processing deposit request.", e);
        }
    }

    public void handleWithdraw(WithdrawRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            String userId = clientHandler.getAuthenticatedId();

            BigDecimal amount = request.getAmount();
            MoneyUtil.requirePositiveMoney(amount, "Withdraw amount");

            User user = authManager.withdraw(userId, amount);
            WalletResponseMessage response = new WalletResponseMessage(
                    MessageType.WALLET_RESPONSE,
                    user.getBalance(),
                    "Withdrawal successful."
            );
            clientHandler.sendResponse(response, request);
            LOGGER.info("User '{}' withdrew ${}.", user.getUsername(), amount);

        } catch (AuctionAppException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Withdrawal failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Withdrawal failed: {}", e.getMessage());
        } catch (Exception e) {
            sendError(clientHandler, request, "An error occurred during withdrawal.");
            LOGGER.error("Error processing withdraw request.", e);
        }
    }

    private void sendError(ClientHandler clientHandler, DepositRequestMessage request, String message) {
        ErrorMessage error = new ErrorMessage(message);
        clientHandler.sendResponse(error, request);
    }

    private void sendError(ClientHandler clientHandler, WithdrawRequestMessage request, String message) {
        ErrorMessage error = new ErrorMessage(message);
        clientHandler.sendResponse(error, request);
    }
}
