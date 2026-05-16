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

    private void updateBalanceDisplay() {
        balanceField.setText(currentBalance.toPlainString());
    }
}
