package net.auctionapp.client.ui.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

public class WalletMenuController implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletMenuController.class);

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
