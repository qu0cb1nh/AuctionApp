package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import net.auctionapp.client.services.WalletService;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.WalletResponseMessage;
import net.auctionapp.common.utils.MoneyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

public class WalletController implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletController.class);

    @FXML
    private TextField balanceField;

    @FXML
    private TextField depositField;

    @FXML
    private TextField withdrawField;

    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        updateBalanceDisplay();
        loadWallet();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        SceneManager.switchScene("MainMenu");
    }

    private void loadWallet() {
        WalletService.getInstance().getWallet(this::handleWalletResponse);
    }

    @FXML
    public void handleDeposit(ActionEvent event) {
        String amountText = depositField.getText().trim();

        if (amountText.isEmpty()) {
            NotificationToastManager.showError("Please enter an amount to deposit.");
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(amountText);
            MoneyUtil.requirePositiveMoney(amount, "Deposit amount");

            WalletService.getInstance().deposit(amount, response -> {
                if (handleWalletResponse(response)) {
                    depositField.clear();
                    NotificationToastManager.showSuccess("Deposit successful! New balance: $" + currentBalance);
                    LOGGER.info("Deposit successful: {}", amount);
                }
            });
        } catch (NumberFormatException e) {
            NotificationToastManager.showError("Please enter a valid number.");
            LOGGER.warn("Invalid deposit amount format: {}", amountText);
        } catch (IllegalArgumentException e) {
            NotificationToastManager.showError(e.getMessage());
            LOGGER.warn("Invalid deposit amount: {}", amountText);
        }
    }

    @FXML
    public void handleWithdraw(ActionEvent event) {
        String amountText = withdrawField.getText().trim();

        if (amountText.isEmpty()) {
            NotificationToastManager.showError("Please enter an amount to withdraw.");
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(amountText);
            MoneyUtil.requirePositiveMoney(amount, "Withdraw amount");

            if (amount.compareTo(currentBalance) > 0) {
                NotificationToastManager.showError("Insufficient balance. Available: $" + currentBalance);
                return;
            }

            WalletService.getInstance().withdraw(amount, response -> {
                if (handleWalletResponse(response)) {
                    withdrawField.clear();
                    NotificationToastManager.showSuccess("Withdrawal successful! New balance: $" + currentBalance);
                    LOGGER.info("Withdrawal successful: {}", amount);
                }
            });
        } catch (NumberFormatException e) {
            NotificationToastManager.showError("Please enter a valid number.");
            LOGGER.warn("Invalid withdraw amount format: {}", amountText);
        } catch (IllegalArgumentException e) {
            NotificationToastManager.showError(e.getMessage());
            LOGGER.warn("Invalid withdraw amount: {}", amountText);
        }
    }

    private void updateBalanceDisplay() {
        balanceField.setText(currentBalance.toPlainString());
    }

    private boolean handleWalletResponse(Message response) {
        if (response instanceof WalletResponseMessage walletResponse) {
            currentBalance = walletResponse.getBalance() == null ? BigDecimal.ZERO : walletResponse.getBalance();
            updateBalanceDisplay();
            return true;
        }
        if (response instanceof ErrorMessage errorMessage) {
            String message = errorMessage.getErrorMessage();
            NotificationToastManager.showError(message == null || message.isBlank()
                    ? "Wallet request failed."
                    : message);
            return false;
        }
        NotificationToastManager.showError("Unexpected response from server.");
        return false;
    }
}
