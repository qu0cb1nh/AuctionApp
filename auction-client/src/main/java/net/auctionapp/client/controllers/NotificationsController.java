package net.auctionapp.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class NotificationsController implements Initializable {

    private static final String LABEL_ACTIVE =
            "-fx-text-fill: #3bb3d1; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String LABEL_INACTIVE =
            "-fx-text-fill: #6b7280; -fx-font-weight: normal; -fx-cursor: hand;";
    private static final String UNDERLINE_ACTIVE =
            "-fx-background-color: #2f80ed; -fx-pref-height: 2;";
    private static final String UNDERLINE_INACTIVE =
            "-fx-background-color: transparent; -fx-pref-height: 2;";

    @FXML
    private Button clearAllButton;
    @FXML
    private HeaderController appHeaderController;

    @FXML
    private VBox notificationCardsContainer;

    @FXML
    private Label filterStatusLabel;

    @FXML
    private Label filterLabelAll;
    @FXML
    private Label filterLabelBids;
    @FXML
    private Label filterLabelMyAuctions;
    @FXML
    private Label filterLabelSystem;
    @FXML
    private Label filterLabelResults;

    @FXML
    private Region filterUnderlineAll;
    @FXML
    private Region filterUnderlineBids;
    @FXML
    private Region filterUnderlineMyAuctions;
    @FXML
    private Region filterUnderlineSystem;
    @FXML
    private Region filterUnderlineResults;

    private Label[] filterLabels;
    private Region[] filterUnderlines;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Notifications", true, "views/MainMenu.fxml");

        filterLabels = new Label[]{
                filterLabelAll, filterLabelBids, filterLabelMyAuctions, filterLabelSystem, filterLabelResults
        };
        filterUnderlines = new Region[]{
                filterUnderlineAll, filterUnderlineBids, filterUnderlineMyAuctions, filterUnderlineSystem,
                filterUnderlineResults
        };
        setActiveFilter(0, "All");
    }

    @FXML
    public void handleClearAll(ActionEvent event) {
        if (notificationCardsContainer != null) {
            notificationCardsContainer.getChildren().clear();
        }
    }

    @FXML
    public void handleFilterAll(MouseEvent event) {
        setActiveFilter(0, "All");
    }

    @FXML
    public void handleFilterBids(MouseEvent event) {
        setActiveFilter(1, "Bids");
    }

    @FXML
    public void handleFilterMyAuctions(MouseEvent event) {
        setActiveFilter(2, "My Auctions");
    }

    @FXML
    public void handleFilterSystem(MouseEvent event) {
        setActiveFilter(3, "System");
    }

    @FXML
    public void handleFilterResults(MouseEvent event) {
        setActiveFilter(4, "Results");
    }

    private void setActiveFilter(int index, String nameForStatus) {
        if (filterLabels == null || filterUnderlines == null) {
            return;
        }
        for (int i = 0; i < filterLabels.length; i++) {
            boolean active = i == index;
            filterLabels[i].setStyle(active ? LABEL_ACTIVE : LABEL_INACTIVE);
            filterUnderlines[i].setStyle(active ? UNDERLINE_ACTIVE : UNDERLINE_INACTIVE);
        }
        if (filterStatusLabel != null) {
            filterStatusLabel.setText("Showing: " + nameForStatus);
        }
    }
}
