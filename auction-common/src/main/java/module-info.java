module net.auctionapp.common {
    requires com.google.gson;
    requires io.github.cdimascio.dotenv.java;

    exports net.auctionapp.common.exceptions;
    exports net.auctionapp.common.messages;
    exports net.auctionapp.common.messages.types;
    exports net.auctionapp.common.models;
    exports net.auctionapp.common.models.auction;
    exports net.auctionapp.common.models.items;
    exports net.auctionapp.common.models.users;
    exports net.auctionapp.common.utils;

    opens net.auctionapp.common.messages to com.google.gson;
    opens net.auctionapp.common.messages.types to com.google.gson;
    opens net.auctionapp.common.models to com.google.gson;
    opens net.auctionapp.common.models.auction to com.google.gson;
    opens net.auctionapp.common.models.items to com.google.gson;
    opens net.auctionapp.common.models.users to com.google.gson;
}

