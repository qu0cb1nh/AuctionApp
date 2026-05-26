package net.auctionapp.client.ui.controllers;

import javafx.event.ActionEvent;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import net.auctionapp.client.services.WalletService;
import net.auctionapp.client.ui.controllers.components.HeaderController;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.wallet.WalletResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ResourceBundle;

public class WalletMenuController implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletMenuController.class);

    @FXML
    private TextField totalBalanceField;

    @FXML
    private TextField availableBalanceField;

    @FXML
    private TextField pendingBalanceField;

    @FXML
    private HeaderController appHeaderController;

    @FXML
    private TextField depositField;

    @FXML
    private TextField withdrawField;

    @FXML
    private Button depositButton;

    @FXML
    private Button withdrawButton;

    private BigDecimal currentBalance = BigDecimal.ZERO;
    private BigDecimal currentPendingBalance = BigDecimal.ZERO;
    private final BooleanProperty mutationPending = new SimpleBooleanProperty();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Wallet");
        depositButton.disableProperty().bind(mutationPending);
        withdrawButton.disableProperty().bind(mutationPending);
        SceneManager.registerSceneMessageListener(MessageType.BALANCE_UPDATE, this::handleBalanceUpdate);
        updateBalanceDisplay();
        requestWallet();
    }

    @FXML
    public void handleDeposit(ActionEvent event) {
        BigDecimal amount = readAmount(depositField, "Deposit amount");
        if (amount == null) {
            return;
        }
        mutationPending.set(true);
        WalletService.getInstance().deposit(
                amount,
                message -> handleWalletMutationResponse(message, depositField)
        );
    }

    @FXML
    public void handleWithdraw(ActionEvent event) {
        BigDecimal amount = readAmount(withdrawField, "Withdrawal amount");
        if (amount == null) {
            return;
        }
        mutationPending.set(true);
        WalletService.getInstance().withdraw(
                amount,
                message -> handleWalletMutationResponse(message, withdrawField)
        );
    }

    private void requestWallet() {
        WalletService.getInstance().requestWallet(this::handleWalletResponse);
    }

    private void handleWalletMutationResponse(Message message, TextField sourceField) {
        mutationPending.set(false);
        if (handleWalletResponse(message) && sourceField != null) {
            sourceField.clear();
        }
    }

    private boolean handleWalletResponse(Message message) {
        if (message instanceof WalletResponseMessage response) {
            currentBalance = defaultMoney(response.getBalance());
            currentPendingBalance = defaultMoney(response.getPendingBalance());
            updateBalanceDisplay();
            if (response.getMessage() != null && !response.getMessage().isBlank()
                    && !"Wallet loaded.".equals(response.getMessage())) {
                NotificationToastManager.showSuccess(response.getMessage());
            }
            return true;
        }
        if (message instanceof ErrorResponseMessage errorMessage) {
            String errorText = errorMessage.getErrorMessage();
            NotificationToastManager.showError(errorText == null || errorText.isBlank()
                    ? "Wallet request failed."
                    : errorText);
            return false;
        }
        NotificationToastManager.showError("Unexpected wallet response.");
        LOGGER.warn("Unexpected wallet response type: {}", message == null ? "null" : message.getType());
        return false;
    }

    private void handleBalanceUpdate(WalletResponseMessage update) {
        currentBalance = defaultMoney(update.getBalance());
        currentPendingBalance = defaultMoney(update.getPendingBalance());
        updateBalanceDisplay();
    }

    private BigDecimal readAmount(TextField field, String label) {
        String value = field.getText() == null ? "" : field.getText().trim().replace(" ", "");
        if (value.isBlank()) {
            NotificationToastManager.showError(label + " is required.");
            return null;
        }
        if (value.matches("\\d{1,3}([.,]\\d{3})+")) {
            value = value.replace(".", "").replace(",", "");
        } else {
            value = value.replace(",", "");
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                NotificationToastManager.showError(label + " must be greater than 0.");
                return null;
            }
            if (amount.stripTrailingZeros().scale() > 2) {
                NotificationToastManager.showError(label + " cannot have more than 2 decimal places.");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            NotificationToastManager.showError(label + " must be a valid number.");
            return null;
        }
    }

    private void updateBalanceDisplay() {
        totalBalanceField.setText(formatMoney(currentBalance.add(currentPendingBalance)));
        availableBalanceField.setText(formatMoney(currentBalance));
        pendingBalanceField.setText(formatMoney(currentPendingBalance));
    }

    private String formatMoney(BigDecimal value) {
        return defaultMoney(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
