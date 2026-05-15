package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import net.auctionapp.client.services.WalletService;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.common.messages.types.WalletResponseMessage;
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
    private BigDecimal currentPendingBalance = BigDecimal.ZERO;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        updateBalanceDisplay();
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

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                NotificationToastManager.showError("Deposit amount must be greater than 0.");
                return;
            }

            WalletService.getInstance().deposit(amount, response -> {
                if (response instanceof WalletResponseMessage walletResponse) {
                    currentBalance = walletResponse.getBalance();
                    currentPendingBalance = walletResponse.getPendingBalance();
                    updateBalanceDisplay();
                    depositField.clear();
                    NotificationToastManager.showSuccess("Deposit successful! New balance: $" + currentBalance);
                    LOGGER.info("Deposit successful: {}", amount);
                } else {
                    NotificationToastManager.showError("Unexpected response from server.");
                }
            });
        } catch (NumberFormatException e) {
            NotificationToastManager.showError("Please enter a valid number.");
            LOGGER.warn("Invalid deposit amount format: {}", amountText);
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

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                NotificationToastManager.showError("Withdraw amount must be greater than 0.");
                return;
            }

            if (amount.compareTo(currentBalance) > 0) {
                NotificationToastManager.showError("Insufficient balance. Available: $" + currentBalance);
                return;
            }

            WalletService.getInstance().withdraw(amount, response -> {
                if (response instanceof WalletResponseMessage walletResponse) {
                    currentBalance = walletResponse.getBalance();
                    currentPendingBalance = walletResponse.getPendingBalance();
                    updateBalanceDisplay();
                    withdrawField.clear();
                    NotificationToastManager.showSuccess("Withdrawal successful! New balance: $" + currentBalance);
                    LOGGER.info("Withdrawal successful: {}", amount);
                } else {
                    NotificationToastManager.showError("Unexpected response from server.");
                }
            });
        } catch (NumberFormatException e) {
            NotificationToastManager.showError("Please enter a valid number.");
            LOGGER.warn("Invalid withdraw amount format: {}", amountText);
        }
    }

    private void updateBalanceDisplay() {
        balanceField.setText(currentBalance.toPlainString());
    }
}
