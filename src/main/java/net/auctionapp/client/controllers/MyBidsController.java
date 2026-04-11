package net.auctionapp.client.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import net.auctionapp.client.SceneNavigator;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class MyBidsController implements Initializable {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private TableView<BidRow> activeTable;
    @FXML
    private TableColumn<BidRow, String> activeAuctionColumn;
    @FXML
    private TableColumn<BidRow, String> activeYourBidColumn;
    @FXML
    private TableColumn<BidRow, String> activeHighestBidColumn;
    @FXML
    private TableColumn<BidRow, String> activeCountdownColumn;
    @FXML
    private TableColumn<BidRow, Void> activeActionColumn;

    @FXML
    private TableView<BidRow> wonTable;
    @FXML
    private TableColumn<BidRow, String> wonAuctionColumn;
    @FXML
    private TableColumn<BidRow, String> wonBidColumn;
    @FXML
    private TableColumn<BidRow, String> wonPaymentColumn;
    @FXML
    private TableColumn<BidRow, String> wonDeadlineColumn;
    @FXML
    private TableColumn<BidRow, Void> wonActionColumn;

    @FXML
    private TableView<BidRow> lostTable;
    @FXML
    private TableColumn<BidRow, String> lostAuctionColumn;
    @FXML
    private TableColumn<BidRow, String> lostYourBidColumn;
    @FXML
    private TableColumn<BidRow, String> lostFinalPriceColumn;
    @FXML
    private TableColumn<BidRow, String> lostEndTimeColumn;
    @FXML
    private TableColumn<BidRow, Void> lostActionColumn;

    @FXML
    private Label activeLabel;
    @FXML
    private Label wonLabel;
    @FXML
    private Label lostLabel;
    @FXML
    private Label statusLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureTables();
        loadPlaceholderBids();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        SceneNavigator.switchScene("views/MainMenu.fxml");
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadPlaceholderBids();
        statusLabel.setText("Data refreshed at " + LocalDateTime.now().format(DATE_TIME_FORMATTER) + ".");
    }

    private void configureTables() {
        activeAuctionColumn.setCellValueFactory(cell -> cell.getValue().col1Property());
        activeYourBidColumn.setCellValueFactory(cell -> cell.getValue().col2Property());
        activeHighestBidColumn.setCellValueFactory(cell -> cell.getValue().col3Property());
        activeCountdownColumn.setCellValueFactory(cell -> cell.getValue().col4Property());
        configureActionColumn(activeActionColumn, "Bid Higher", "Open auction details for: ");

        wonAuctionColumn.setCellValueFactory(cell -> cell.getValue().col1Property());
        wonBidColumn.setCellValueFactory(cell -> cell.getValue().col2Property());
        wonPaymentColumn.setCellValueFactory(cell -> cell.getValue().col3Property());
        wonDeadlineColumn.setCellValueFactory(cell -> cell.getValue().col4Property());
        configureActionColumn(wonActionColumn, "Pay Now", "Open payment page for: ");

        lostAuctionColumn.setCellValueFactory(cell -> cell.getValue().col1Property());
        lostYourBidColumn.setCellValueFactory(cell -> cell.getValue().col2Property());
        lostFinalPriceColumn.setCellValueFactory(cell -> cell.getValue().col3Property());
        lostEndTimeColumn.setCellValueFactory(cell -> cell.getValue().col4Property());
        configureActionColumn(lostActionColumn, "View Similar", "Search similar auctions for: ");

        activeTable.setPlaceholder(new Label("No active bids."));
        wonTable.setPlaceholder(new Label("No won auctions."));
        lostTable.setPlaceholder(new Label("No lost auctions."));
    }

    private void configureActionColumn(TableColumn<BidRow, Void> column, String buttonText, String messagePrefix) {
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button actionButton = createActionButton(buttonText);

            {
                actionButton.setOnAction(event -> {
                    BidRow row = getTableView().getItems().get(getIndex());
                    statusLabel.setText(messagePrefix + row.getCol1());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actionButton);
            }
        });
    }

    private Button createActionButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #2f4f69; -fx-text-fill: white; -fx-background-radius: 14px; -fx-cursor: hand; -fx-font-size: 12px;");
        return button;
    }

    private void loadPlaceholderBids() {
        ObservableList<BidRow> activeRows = FXCollections.observableArrayList(
                new BidRow("Iphone 17 Pro Max 1TB", "$1,220", "$1,240", "00:18:25"),
                new BidRow("Vintage Camera Collection", "$840", "$840", "01:42:10"),
                new BidRow("Gaming Laptop RTX", "$1,410", "$1,425", "00:52:02")
        );

        ObservableList<BidRow> wonRows = FXCollections.observableArrayList(
                new BidRow("Abstract Art Painting", "$520", "Pending payment", "2026-04-12 22:00"),
                new BidRow("Limited Sneaker Set", "$310", "Pending payment", "2026-04-13 09:30")
        );

        ObservableList<BidRow> lostRows = FXCollections.observableArrayList(
                new BidRow("Mechanical Keyboard Pro", "$180", "$195", "2026-04-10 21:00"),
                new BidRow("Smartwatch Ultra", "$430", "$460", "2026-04-09 20:45")
        );

        activeTable.setItems(activeRows);
        wonTable.setItems(wonRows);
        lostTable.setItems(lostRows);

        activeLabel.setText(String.valueOf(activeRows.size()));
        wonLabel.setText(String.valueOf(wonRows.size()));
        lostLabel.setText(String.valueOf(lostRows.size()));

        statusLabel.setText("Loaded " + (activeRows.size() + wonRows.size() + lostRows.size()) + " bid records.");
    }

    public static class BidRow {
        private final StringProperty col1;
        private final StringProperty col2;
        private final StringProperty col3;
        private final StringProperty col4;

        public BidRow(String col1, String col2, String col3, String col4) {
            this.col1 = new SimpleStringProperty(col1);
            this.col2 = new SimpleStringProperty(col2);
            this.col3 = new SimpleStringProperty(col3);
            this.col4 = new SimpleStringProperty(col4);
        }

        public StringProperty col1Property() {
            return col1;
        }

        public StringProperty col2Property() {
            return col2;
        }

        public StringProperty col3Property() {
            return col3;
        }

        public StringProperty col4Property() {
            return col4;
        }

        public String getCol1() {
            return col1.get();
        }
    }
}
