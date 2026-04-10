package net.auctionapp.client.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
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
    private TableView<BidRow> bidsTable;
    @FXML
    private TableColumn<BidRow, String> auctionTitleColumn;
    @FXML
    private TableColumn<BidRow, String> yourBidColumn;
    @FXML
    private TableColumn<BidRow, String> currentBidColumn;
    @FXML
    private TableColumn<BidRow, String> statusColumn;
    @FXML
    private TableColumn<BidRow, String> endTimeColumn;

    @FXML
    private Label totalBidsLabel;
    @FXML
    private Label leadingLabel;
    @FXML
    private Label wonLabel;
    @FXML
    private Label outbidLabel;
    @FXML
    private Label statusLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureTable();
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

    private void configureTable() {
        auctionTitleColumn.setCellValueFactory(cell -> cell.getValue().auctionTitleProperty());
        yourBidColumn.setCellValueFactory(cell -> cell.getValue().yourBidProperty());
        currentBidColumn.setCellValueFactory(cell -> cell.getValue().currentBidProperty());
        statusColumn.setCellValueFactory(cell -> cell.getValue().statusProperty());
        endTimeColumn.setCellValueFactory(cell -> cell.getValue().endTimeProperty());
    }

    private void loadPlaceholderBids() {
        ObservableList<BidRow> rows = FXCollections.observableArrayList(
                new BidRow("Iphone 17 Pro Max 1TB", "$1,220", "$1,230", "OUTBID", "2026-04-12 21:00"),
                new BidRow("Vintage Camera Collection", "$840", "$840", "LEADING", "2026-04-11 18:30"),
                new BidRow("Abstract Art Painting", "$520", "$520", "WON", "2026-04-09 20:00"),
                new BidRow("Gaming Laptop RTX", "$1,410", "$1,425", "OUTBID", "2026-04-10 23:15")
        );

        bidsTable.setItems(rows);
        bidsTable.setPlaceholder(new Label("No bids found."));
        updateStats(rows);
        statusLabel.setText(rows.isEmpty() ? "You have no bids yet." : "Loaded " + rows.size() + " bid entries.");
    }

    private void updateStats(ObservableList<BidRow> rows) {
        long leadingCount = rows.stream().filter(row -> "LEADING".equals(row.getStatus())).count();
        long wonCount = rows.stream().filter(row -> "WON".equals(row.getStatus())).count();
        long outbidCount = rows.stream().filter(row -> "OUTBID".equals(row.getStatus())).count();

        totalBidsLabel.setText(String.valueOf(rows.size()));
        leadingLabel.setText(String.valueOf(leadingCount));
        wonLabel.setText(String.valueOf(wonCount));
        outbidLabel.setText(String.valueOf(outbidCount));
    }

    public static class BidRow {
        private final StringProperty auctionTitle;
        private final StringProperty yourBid;
        private final StringProperty currentBid;
        private final StringProperty status;
        private final StringProperty endTime;

        public BidRow(String auctionTitle, String yourBid, String currentBid, String status, String endTime) {
            this.auctionTitle = new SimpleStringProperty(auctionTitle);
            this.yourBid = new SimpleStringProperty(yourBid);
            this.currentBid = new SimpleStringProperty(currentBid);
            this.status = new SimpleStringProperty(status);
            this.endTime = new SimpleStringProperty(endTime);
        }

        public StringProperty auctionTitleProperty() {
            return auctionTitle;
        }

        public StringProperty yourBidProperty() {
            return yourBid;
        }

        public StringProperty currentBidProperty() {
            return currentBid;
        }

        public StringProperty statusProperty() {
            return status;
        }

        public StringProperty endTimeProperty() {
            return endTime;
        }

        public String getStatus() {
            return status.get();
        }
    }
}

