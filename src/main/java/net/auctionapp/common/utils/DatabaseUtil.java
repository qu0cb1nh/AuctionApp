package net.auctionapp.common.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseUtil {

    private DatabaseUtil() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(
                    ConfigUtil.getDatabaseUrl(),
                    ConfigUtil.getDatabaseUser(),
                    ConfigUtil.getDatabasePassword()
            );
            System.out.println("Connection SUCCESS!");
            return conn;
        } catch (SQLException e) {
            e.printStackTrace();
            throw e;
        }
    }
}

