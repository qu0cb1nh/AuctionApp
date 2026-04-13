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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MyBidsController implements Initializable {
    @FXML
    private FlowPane bidFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;

    private final List<BidCard> allUserBids = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusFilterComboBox.getItems().setAll("All", "RUNNING", "FINISHED", "OUTBID");
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
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadMyBids() {
        String currentUser = resolveCurrentUsername();
        List<BidCard> demoBids = buildDemoBids();

        allUserBids.clear();
        allUserBids.addAll(
                demoBids.stream()
                        .filter(bid -> bid.bidderUsername().equalsIgnoreCase(currentUser))
                        .sorted(Comparator.comparing(BidCard::auctionTitle))
                        .toList()
        );
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();

        List<BidCard> filtered = allUserBids.stream()
                .filter(bid -> search.isBlank() || bid.auctionTitle().toLowerCase(Locale.ROOT).contains(search))
                .filter(bid -> selectedStatus == null || "All".equals(selectedStatus) || bid.status().equalsIgnoreCase(selectedStatus))
                .collect(Collectors.toList());

        renderBidCards(filtered);
        statusLabel.setText("Showing " + filtered.size() + " bids.");
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
        card.setPrefSize(200, 280);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0); "
                + "-fx-padding: 10;");

        ImageView imageView = createItemImage(bid.imagePath());

        Label title = new Label(bid.auctionTitle());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label yourBid = new Label("Your Bid: " + bid.yourBid());
        Label highestBid = new Label("Highest Bid: " + bid.highestBid());
        Label endTime = new Label("End Time: " + bid.endTime());
        Label status = new Label("Status: " + bid.status());
        status.setStyle("-fx-text-fill: #c13c21; -fx-font-weight: bold;");

        Button viewButton = new Button("Bid now!");
        viewButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        viewButton.setOnAction(event -> {
            statusLabel.setText("Opening auction: " + bid.auctionTitle());
            SceneNavigator.switchScene("views/AuctionItem.fxml");
        });

        HBox buttonRow = new HBox(viewButton);
        buttonRow.setStyle("-fx-alignment: center-right;");

        card.getChildren().addAll(imageView, title, yourBid, highestBid, endTime, status, buttonRow);
        return card;
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

    private List<BidCard> buildDemoBids() {
        return List.of(
                new BidCard("admin", "Iphone 17 Pro Max 1TB", "$1,220", "$1,240", "2026-04-14 18:00", "RUNNING", "images/iphone.jpg"),
                new BidCard("admin", "Gaming Laptop RTX", "$1,410", "$1,425", "2026-04-13 23:00", "OUTBID", "images/iphone.jpg"),
                new BidCard("admin", "Vintage Camera Collection", "$840", "$840", "2026-04-14 10:00", "RUNNING", "images/iphone.jpg"),
                new BidCard("bidderA", "Electric Scooter Pro", "$620", "$650", "2026-04-12 20:30", "FINISHED", "images/iphone.jpg")
        );
    }

    private String resolveCurrentUsername() {
        if (ClientApp.getInstance() == null || ClientApp.getInstance().getCurrentUsername() == null
                || ClientApp.getInstance().getCurrentUsername().isBlank()) {
            return "admin";
        }
        return ClientApp.getInstance().getCurrentUsername();
    }

    private record BidCard(String bidderUsername, String auctionTitle, String yourBid,
                           String highestBid, String endTime, String status, String imagePath) {
    }
}
