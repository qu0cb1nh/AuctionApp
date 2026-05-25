module net.auctionapp.common {
    requires com.google.gson;
    requires io.github.cdimascio.dotenv.java;
    requires org.slf4j;

    exports net.auctionapp.common.exceptions;
    exports net.auctionapp.common.messages;
    exports net.auctionapp.common.messages.admin;
    exports net.auctionapp.common.messages.auction;
    exports net.auctionapp.common.messages.auth;
    exports net.auctionapp.common.messages.notification;
    exports net.auctionapp.common.messages.system;
    exports net.auctionapp.common.messages.wallet;
    exports net.auctionapp.common.messages.watchlist;
    exports net.auctionapp.common.dto;
    exports net.auctionapp.common.auction;
    exports net.auctionapp.common.items;
    exports net.auctionapp.common.users;
    exports net.auctionapp.common.utils;

    opens net.auctionapp.common.messages to com.google.gson;
    opens net.auctionapp.common.messages.admin to com.google.gson;
    opens net.auctionapp.common.messages.auction to com.google.gson;
    opens net.auctionapp.common.messages.auth to com.google.gson;
    opens net.auctionapp.common.messages.notification to com.google.gson;
    opens net.auctionapp.common.messages.system to com.google.gson;
    opens net.auctionapp.common.messages.wallet to com.google.gson;
    opens net.auctionapp.common.messages.watchlist to com.google.gson;
    opens net.auctionapp.common.dto to com.google.gson;
    opens net.auctionapp.common.auction to com.google.gson;
    opens net.auctionapp.common.items to com.google.gson;
    opens net.auctionapp.common.users to com.google.gson;
    exports net.auctionapp.common.notifications;
    opens net.auctionapp.common.notifications to com.google.gson;
}
