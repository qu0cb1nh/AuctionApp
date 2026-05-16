package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.DepositRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.WithdrawRequestMessage;
import net.auctionapp.common.messages.types.WalletResponseMessage;
import net.auctionapp.common.models.users.User;
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

    public void handleDeposit(DepositRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            String userId = clientHandler.getAuthenticatedId();

            BigDecimal amount = request.getAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendError(clientHandler, request, "Deposit amount must be greater than 0.");
                return;
            }

            User user = authManager.deposit(userId, amount);
            WalletResponseMessage response = new WalletResponseMessage(
                    MessageType.WALLET_RESPONSE,
                    user.getBalance(),
                    user.getPendingBalance(),
                    "Deposit successful."
            );
            clientHandler.sendResponse(response, request);
            LOGGER.info("User '{}' deposited ${}.", user.getUsername(), amount);

        } catch (AuctionAppException e) {
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
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendError(clientHandler, request, "Withdraw amount must be greater than 0.");
                return;
            }

            User user = authManager.withdraw(userId, amount);
            WalletResponseMessage response = new WalletResponseMessage(
                    MessageType.WALLET_RESPONSE,
                    user.getBalance(),
                    user.getPendingBalance(),
                    "Withdrawal successful."
            );
            clientHandler.sendResponse(response, request);
            LOGGER.info("User '{}' withdrew ${}.", user.getUsername(), amount);

        } catch (AuctionAppException e) {
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
