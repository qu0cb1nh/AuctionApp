module net.auctionapp.server {
    requires java.sql;
    requires net.auctionapp.common;
    requires com.zaxxer.hikari;
    requires cloudinary.core;
    requires cloudinary.http5;
    requires org.slf4j;
    requires jbcrypt;
    requires mysql.connector.j;

    exports net.auctionapp.server;
    exports net.auctionapp.server.config;
}
