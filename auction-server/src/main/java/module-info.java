module net.auctionapp.server {
    requires java.sql;
    requires net.auctionapp.common;
    requires com.zaxxer.hikari;
    requires org.slf4j;
    requires jbcrypt;
    requires mysql.connector.j;

    exports net.auctionapp.server;
}

