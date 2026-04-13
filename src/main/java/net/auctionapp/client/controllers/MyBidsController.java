package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MyBidsController implements Initializable {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private FlowPane bidFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label activeLabel;
    @FXML
    private Label wonLabel;
    @FXML
    private Label lostLabel;
    @FXML
    private Label statusLabel;

    private final List<BidCard> allMyBids = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusFilterComboBox.getItems().setAll("All", "RUNNING", "WON", "LOST");
        statusFilterComboBox.getSelectionModel().selectFirst();
        loadMyBids();
        applyFilters();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        SceneNavigator.switchScene("views/MainMenu.fxml");
    }

    @FXML
    public void handleSignOut(ActionEvent event) {
        if (ClientApp.getInstance() != null) {
            ClientApp.getInstance().setCurrentUser(null, null);
        }
        SceneNavigator.switchScene("views/LoginMenu.fxml");
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadMyBids();
        applyFilters();
        statusLabel.setText("Data refreshed at " + LocalDateTime.now().format(DATE_TIME_FORMATTER) + ".");
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadMyBids() {
        String currentUser = resolveCurrentUsername();
        List<BidCard> demoBids = buildDemoBids();

        allMyBids.clear();
        allMyBids.addAll(demoBids.stream()
                .filter(bid -> bid.bidderUsername().equalsIgnoreCase(currentUser))
                .sorted(Comparator.comparing(BidCard::title))
                .toList());

        updateCounters();
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();

        List<BidCard> filtered = allMyBids.stream()
                .filter(bid -> search.isBlank() || bid.title().toLowerCase(Locale.ROOT).contains(search))
                .filter(bid -> selectedStatus == null || "All".equals(selectedStatus)
                        || bid.status().equalsIgnoreCase(selectedStatus))
                .collect(Collectors.toList());

        renderBidCards(filtered);
        statusLabel.setText("Showing " + filtered.size() + " bids.");
    }

    private void updateCounters() {
        long running = allMyBids.stream().filter(bid -> "RUNNING".equalsIgnoreCase(bid.status())).count();
        long won = allMyBids.stream().filter(bid -> "WON".equalsIgnoreCase(bid.status())).count();
        long lost = allMyBids.stream().filter(bid -> "LOST".equalsIgnoreCase(bid.status())).count();

        activeLabel.setText(String.valueOf(running));
        wonLabel.setText(String.valueOf(won));
        lostLabel.setText(String.valueOf(lost));
    }

    private void renderBidCards(List<BidCard> bids) {
        bidFlowPane.getChildren().clear();
        if (bids.isEmpty()) {
            Label emptyLabel = new Label("No bids found for your account.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            bidFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (BidCard bid : bids) {
            bidFlowPane.getChildren().add(createBidCard(bid));
        }
    }

    private VBox createBidCard(BidCard bid) {
        VBox card = new VBox(8);
        card.setPrefSize(200, 300);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0); "
                + "-fx-padding: 10;");

        ImageView imageView = createItemImage(bid.imagePath());

        Label title = new Label(bid.title());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label yourBid = new Label("Your Bid: " + bid.yourBid());
        Label highestBid = new Label("Highest Bid: " + bid.highestBid());
        Label endTime = new Label("End Time: " + bid.endTime());
        Label status = new Label("Status: " + bid.status());
        status.setStyle(resolveStatusStyle(bid.status()));

        Button actionButton = new Button(resolveActionText(bid.status()));
        actionButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        actionButton.setOnAction(event -> {
            statusLabel.setText("Opening auction: " + bid.title());
            SceneNavigator.switchScene("views/AuctionItem.fxml");
        });

        HBox buttonRow = new HBox(actionButton);
        buttonRow.setStyle("-fx-alignment: center-right;");

        card.getChildren().addAll(imageView, title, yourBid, highestBid, endTime, status, buttonRow);
        return card;
    }

    private String resolveStatusStyle(String status) {
        if ("WON".equalsIgnoreCase(status)) {
            return "-fx-text-fill: #1f9d55; -fx-font-weight: bold;";
        }
        if ("LOST".equalsIgnoreCase(status)) {
            return "-fx-text-fill: #d64541; -fx-font-weight: bold;";
        }
        return "-fx-text-fill: #c13c21; -fx-font-weight: bold;";
    }

    private String resolveActionText(String status) {
        if ("WON".equalsIgnoreCase(status)) {
            return "View Payment";
        }
        if ("LOST".equalsIgnoreCase(status)) {
            return "View Similar";
        }
        return "Bid Now";
    }

    private ImageView createItemImage(String imagePath) {
        ImageView imageView = new ImageView();
        imageView.setFitHeight(150);
        imageView.setFitWidth(200);
        imageView.setPreserveRatio(true);
        imageView.setPickOnBounds(true);

        String resolvedPath = (imagePath == null || imagePath.isBlank()) ? "images/iphone.jpg" : imagePath;
        URL url = getClass().getResource("/net/auctionapp/client/views/" + resolvedPath);
        if (url == null) {
            url = getClass().getResource("/net/auctionapp/client/views/images/iphone.jpg");
        }
        if (url != null) {
            imageView.setImage(new Image(url.toExternalForm()));
        }
        return imageView;
    }

    private String resolveCurrentUsername() {
        if (ClientApp.getInstance() == null || ClientApp.getInstance().getCurrentUsername() == null
                || ClientApp.getInstance().getCurrentUsername().isBlank()) {
            return "admin";
        }
        return ClientApp.getInstance().getCurrentUsername();
    }

    private List<BidCard> buildDemoBids() {
        return List.of(
                new BidCard("admin", "Iphone 17 Pro Max 1TB", "$1,220", "$1,240", "2026-04-14 18:00", "RUNNING", "images/iphone.jpg"),
                new BidCard("admin", "Gaming Laptop RTX", "$1,410", "$1,425", "2026-04-13 23:00", "RUNNING", "images/iphone.jpg"),
                new BidCard("admin", "Vintage Camera Collection", "$840", "$840", "2026-04-12 10:00", "WON", "images/iphone.jpg"),
                new BidCard("admin", "Collector Keyboard", "$180", "$195", "2026-04-11 20:30", "LOST", "images/iphone.jpg"),
                new BidCard("bidderB", "Limited Sneaker Set", "$310", "$320", "2026-04-13 09:30", "RUNNING", "images/iphone.jpg")
        );
    }

    private record BidCard(
            String bidderUsername,
            String title,
            String yourBid,
            String highestBid,
            String endTime,
            String status,
            String imagePath
    ) {
    }
}
