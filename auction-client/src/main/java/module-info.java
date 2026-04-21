module net.auctionapp.client {
    requires net.auctionapp.common;
    requires javafx.controls;
    requires javafx.fxml;

    exports net.auctionapp.client;
    opens net.auctionapp.client to javafx.fxml;
    opens net.auctionapp.client.controllers to javafx.fxml;
}
