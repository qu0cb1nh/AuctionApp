package net.auctionapp.client.controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.SceneNavigator;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.AuctionSummary;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;

import java.math.BigDecimal;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class AuctionListController implements Initializable {
    @FXML
    private HeaderController appHeaderController;
    @FXML
    private FlowPane auctionFlowPane;
    @FXML
    private Label listStatusLabel;

    private Consumer<Message> listResponseListener;
    private Consumer<Message> errorListener;

    @FXML
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Explore Auctions", true, "MainMenu");
        listResponseListener = this::handleAuctionListResponse;
        errorListener = this::handleErrorResponse;
        ClientApp.getInstance().addMessageHandler(MessageType.AUCTION_LIST_RESPONSE, listResponseListener);
        ClientApp.getInstance().addMessageHandler(MessageType.ERROR, errorListener);
        requestAuctionList();
    }

    private void requestAuctionList() {
        listStatusLabel.setText("Loading auctions...");
        ClientApp.getInstance().getNetworkService().sendMessage(new GetAuctionListRequestMessage());
    }

    private void handleAuctionListResponse(Message message) {
        if (!(message instanceof AuctionListResponseMessage response)) {
            return;
        }

        List<AuctionSummary> auctions = response.getAuctions();
        if (auctions == null) {
            auctions = Collections.emptyList();
        }
        renderAuctionCards(auctions);
    }

    private void handleErrorResponse(Message message) {
        if (!(message instanceof ErrorMessage errorMessage)) {
            return;
        }
        listStatusLabel.setStyle("-fx-text-fill: #d9534f;");
        listStatusLabel.setText(errorMessage.getErrorMessage());
    }

    private void renderAuctionCards(List<AuctionSummary> auctions) {
        auctionFlowPane.getChildren().clear();
        if (auctions.isEmpty()) {
            listStatusLabel.setStyle("-fx-text-fill: #666666;");
            listStatusLabel.setText("No auctions available.");
            return;
        }

        for (AuctionSummary auction : auctions) {
            auctionFlowPane.getChildren().add(createAuctionCard(auction));
        }
        listStatusLabel.setStyle("-fx-text-fill: #666666;");
        listStatusLabel.setText("Loaded " + auctions.size() + " auction(s).");
    }

    private VBox createAuctionCard(AuctionSummary auction) {
        Label titleLabel = new Label(auction.getTitle());
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label priceLabel = new Label("Current Bid: " + formatPrice(auction.getCurrentPrice()));
        priceLabel.setStyle("-fx-text-fill: #c13c21;");

        Label statusLabel = new Label("Status: " + auction.getStatus().name());
        statusLabel.setStyle("-fx-text-fill: #666666;");

        Button bidButton = new Button("Bid now!");
        bidButton.setStyle("-fx-background-color: #3bb3d1; -fx-text-fill: white; -fx-background-radius: 5;");
        bidButton.setOnAction(event -> handleViewItem(auction.getAuctionId()));

        VBox card = new VBox(10.0, titleLabel, priceLabel, statusLabel, bidButton);
        card.setPadding(new Insets(10.0));
        card.setPrefWidth(200.0);
        card.setPrefHeight(180.0);
        card.setStyle(
                "-fx-background-color: white; "
                        + "-fx-background-radius: 10; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0);"
        );
        return card;
    }

    private String formatPrice(BigDecimal value) {
        if (value == null) {
            return "N/A";
        }
        return "$" + value.toPlainString();
    }

    private void handleViewItem(String auctionId) {
        cleanupHandlers();
        ClientApp.getInstance().setSelectedAuctionId(auctionId);
        SceneNavigator.switchScene("AuctionItem");
    }

    @FXML
    public void handleSignOut() {
        cleanupHandlers();
        SceneNavigator.switchScene("LoginMenu");
    }

    @FXML
    public void handleBackToMainMenu() {
        cleanupHandlers();
        SceneNavigator.switchScene("MainMenu");
    }

    private void cleanupHandlers() {
        if (listResponseListener != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.AUCTION_LIST_RESPONSE, listResponseListener);
        }
        if (errorListener != null) {
            ClientApp.getInstance().removeMessageHandler(MessageType.ERROR, errorListener);
        }
    }
}
