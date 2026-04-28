module net.auctionapp.client {
    requires net.auctionapp.common;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.slf4j;

    exports net.auctionapp.client;
    opens net.auctionapp.client to javafx.fxml;
    opens net.auctionapp.client.controllers to javafx.fxml;
    exports net.auctionapp.client.controllers;
    exports net.auctionapp.client.utils;
    opens net.auctionapp.client.utils to javafx.fxml;
}
