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

public class MyListingsController implements Initializable {
    @FXML
    private FlowPane listingFlowPane;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Label statusLabel;

    private final List<ListingCard> allMyListings = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusFilterComboBox.getItems().setAll("All", "RUNNING", "DRAFT", "FINISHED", "CANCELED");
        statusFilterComboBox.getSelectionModel().selectFirst();
        loadMyListings();
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
        loadMyListings();
        applyFilters();
    }

    @FXML
    public void handleFilterChanged() {
        applyFilters();
    }

    private void loadMyListings() {
        String currentUser = resolveCurrentUsername();
        List<ListingCard> demoListings = buildDemoListings();

        allMyListings.clear();
        allMyListings.addAll(
                demoListings.stream()
                        .filter(listing -> listing.sellerUsername().equalsIgnoreCase(currentUser))
                        .sorted(Comparator.comparing(ListingCard::title))
                        .toList()
        );
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getSelectionModel().getSelectedItem();

        List<ListingCard> filtered = allMyListings.stream()
                .filter(listing -> search.isBlank() || listing.title().toLowerCase(Locale.ROOT).contains(search))
                .filter(listing -> selectedStatus == null || "All".equals(selectedStatus)
                        || listing.status().equalsIgnoreCase(selectedStatus))
                .collect(Collectors.toList());

        renderListingCards(filtered);
        statusLabel.setText("Showing " + filtered.size() + " listings.");
    }

    private void renderListingCards(List<ListingCard> listings) {
        listingFlowPane.getChildren().clear();
        if (listings.isEmpty()) {
            Label emptyLabel = new Label("No listings found for your account.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4a5f73;");
            listingFlowPane.getChildren().add(emptyLabel);
            return;
        }

        for (ListingCard listing : listings) {
            listingFlowPane.getChildren().add(createListingCard(listing));
        }
    }

    private VBox createListingCard(ListingCard listing) {
        VBox card = new VBox(8);
        card.setPrefSize(200, 280);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0); "
                + "-fx-padding: 10;");

        ImageView imageView = createItemImage(listing.imagePath());

        Label title = new Label(listing.title());
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label startPrice = new Label("Start Price: " + listing.startPrice());
        Label currentBid = new Label("Current Bid: " + listing.currentBid());
        Label endTime = new Label("End Time: " + listing.endTime());
        Label status = new Label("Status: " + listing.status());
        status.setStyle("-fx-text-fill: #c13c21; -fx-font-weight: bold;");

        Button actionButton = new Button("View Item");
        actionButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        actionButton.setOnAction(event -> {
            statusLabel.setText("Opening listing: " + listing.title());
            SceneNavigator.switchScene("views/AuctionItem.fxml");
        });

        HBox buttonRow = new HBox(actionButton);
        buttonRow.setStyle("-fx-alignment: center-right;");

        card.getChildren().addAll(imageView, title, startPrice, currentBid, endTime, status, buttonRow);
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

    private String resolveCurrentUsername() {
        if (ClientApp.getInstance() == null || ClientApp.getInstance().getCurrentUsername() == null
                || ClientApp.getInstance().getCurrentUsername().isBlank()) {
            return "admin";
        }
        return ClientApp.getInstance().getCurrentUsername();
    }

    private List<ListingCard> buildDemoListings() {
        return List.of(
                new ListingCard("admin", "Iphone 17 Pro Max 1TB", "$1,000", "$1,220", "2026-04-14 18:00", "RUNNING", "images/iphone.jpg"),
                new ListingCard("admin", "Gaming Laptop RTX", "$1,250", "$1,410", "2026-04-13 23:00", "RUNNING", "images/iphone.jpg"),
                new ListingCard("admin", "Vintage Camera Collection", "$700", "$840", "2026-04-14 10:00", "DRAFT", "images/iphone.jpg"),
                new ListingCard("sellerB", "Collector Skateboard", "$150", "$210", "2026-04-12 12:00", "FINISHED", "images/iphone.jpg")
        );
    }

    private record ListingCard(
            String sellerUsername,
            String title,
            String startPrice,
            String currentBid,
            String endTime,
            String status,
            String imagePath
    ) {
    }
}

