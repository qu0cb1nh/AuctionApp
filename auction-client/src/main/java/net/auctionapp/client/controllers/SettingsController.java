package net.auctionapp.client.controllers;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {
    @FXML
    private HeaderController appHeaderController;
    @FXML
    private TextField displayNameField;
    @FXML
    private ComboBox<String> currencyComboBox;
    @FXML
    private ComboBox<String> themeComboBox;
    @FXML
    private CheckBox notificationsCheckBox;
    @FXML
    private Spinner<Integer> autoRefreshSpinner;
    @FXML
    private Label statusLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Settings", true, "views/MainMenu.fxml");

        currencyComboBox.setItems(FXCollections.observableArrayList("USD", "VND", "EUR"));
        themeComboBox.setItems(FXCollections.observableArrayList("Ocean", "Light", "Dark"));
        autoRefreshSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 120, 15, 5));
        applyDefaults();

        String username = ClientApp.getInstance() != null ? ClientApp.getInstance().getCurrentUsername() : "";
        displayNameField.setText(username == null ? "" : username);
        statusLabel.setText("Ready.");
    }

    @FXML
    public void handleBack(ActionEvent event) {
        SceneNavigator.switchScene("views/MainMenu.fxml");
    }

    @FXML
    public void handleResetDefaults(ActionEvent event) {
        applyDefaults();
        statusLabel.setText("Settings reset to default values.");
    }

    @FXML
    public void handleSaveSettings(ActionEvent event) {
        String currency = currencyComboBox.getValue();
        Integer refresh = autoRefreshSpinner.getValue();
        boolean notifications = notificationsCheckBox.isSelected();

        statusLabel.setText(
                "Settings saved: currency=" + currency
                        + ", refresh=" + refresh + "s"
                        + ", notifications=" + notifications + "."
        );
    }

    private void applyDefaults() {
        currencyComboBox.setValue("USD");
        themeComboBox.setValue("Ocean");
        notificationsCheckBox.setSelected(true);
        autoRefreshSpinner.getValueFactory().setValue(15);
    }
}
