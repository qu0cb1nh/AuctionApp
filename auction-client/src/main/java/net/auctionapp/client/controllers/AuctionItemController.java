package net.auctionapp.client.controllers;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import net.auctionapp.client.SceneNavigator;
public class AuctionItemController {
    @FXML
    private HeaderController appHeaderController;

    @FXML private ImageView productImageView;
    @FXML private Label productNameLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label currentBidLabel;
    @FXML private Label timeRemainingLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button placeBidButton;
    @FXML private Label messageLabel;
    @FXML private LineChart<String, Number> priceHistoryChart;

    private double currentHighestBid = 1199.0;

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Auction Details", true, "views/AuctionList.fxml");
        messageLabel.setText("");
    }

    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            String input = bidAmountField.getText();
            double newBid = Double.parseDouble(input);

            if (newBid <= currentHighestBid) {
                messageLabel.setText("Bid must be higher than $" + currentHighestBid);
                messageLabel.setStyle("-fx-text-fill: red;");
            } else {
                currentHighestBid = newBid;
                currentBidLabel.setText("$" + currentHighestBid);
                messageLabel.setText("Bid placed successfully.");
                messageLabel.setStyle("-fx-text-fill: green;");
                bidAmountField.clear();
            }
        } catch (NumberFormatException e) {
            messageLabel.setText("Please enter a valid number.");
            messageLabel.setStyle("-fx-text-fill: red;");
        }
    }
    @FXML
    public void handleBack(ActionEvent event) {
        SceneNavigator.switchScene("views/AuctionList.fxml");
    }
}
