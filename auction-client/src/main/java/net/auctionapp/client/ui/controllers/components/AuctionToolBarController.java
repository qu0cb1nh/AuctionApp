package net.auctionapp.client.ui.controllers.components;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AuctionToolBarController {
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Button refreshButton;

    public void setup(
            String searchPrompt,
            List<String> filters,
            String defaultFilter,
            Runnable onFilterChanged,
            Runnable onRefresh
    ) {
        Objects.requireNonNull(onFilterChanged, "onFilterChanged");
        Objects.requireNonNull(onRefresh, "onRefresh");

        searchField.setPromptText(searchPrompt);
        statusFilterComboBox.setOnAction(null);
        statusFilterComboBox.getItems().setAll(filters);
        statusFilterComboBox.getSelectionModel().select(defaultFilter);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> onFilterChanged.run());
        statusFilterComboBox.setOnAction(event -> onFilterChanged.run());
        refreshButton.setOnAction(event -> onRefresh.run());
    }

    public String getSearchText() {
        return searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    public String getSelectedFilter() {
        return statusFilterComboBox.getSelectionModel().getSelectedItem();
    }
}
