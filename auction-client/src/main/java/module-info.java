module net.auctionapp.client {
    requires net.auctionapp.common;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires org.slf4j;

    exports net.auctionapp.client;
    opens net.auctionapp.client to javafx.fxml;
    opens net.auctionapp.client.ui.controllers to javafx.fxml;
    opens net.auctionapp.client.ui.controllers.components to javafx.fxml;
    exports net.auctionapp.client.ui.controllers;
    exports net.auctionapp.client.utils;
    opens net.auctionapp.client.utils to javafx.fxml;
    exports net.auctionapp.client.ui.managers;
    opens net.auctionapp.client.ui.managers to javafx.fxml;
}
