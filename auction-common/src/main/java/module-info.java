module net.auctionapp.common {
    requires com.google.gson;
    requires io.github.cdimascio.dotenv.java;
    requires org.slf4j;

    exports net.auctionapp.common.exceptions;
    exports net.auctionapp.common.messages;
    exports net.auctionapp.common.messages.types;
    exports net.auctionapp.common.auction;
    exports net.auctionapp.common.items;
    exports net.auctionapp.common.users;
    exports net.auctionapp.common.utils;

    opens net.auctionapp.common.messages to com.google.gson;
    opens net.auctionapp.common.messages.types to com.google.gson;
    opens net.auctionapp.common.auction to com.google.gson;
    opens net.auctionapp.common.items to com.google.gson;
    opens net.auctionapp.common.users to com.google.gson;
    exports net.auctionapp.common.notifications;
    opens net.auctionapp.common.notifications to com.google.gson;
}

