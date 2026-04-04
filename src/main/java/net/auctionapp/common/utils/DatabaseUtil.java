package net.auctionapp.common.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseUtil {
    private static HikariDataSource DATA_SOURCE;

    public static void createConnectionPool() { // Creates a database connection pool
        if(DATA_SOURCE != null) {
            return;
        }
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(ConfigUtil.getDatabaseUrl());
            config.setUsername(ConfigUtil.getDatabaseUser());
            config.setPassword(ConfigUtil.getDatabasePassword());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setPoolName("AuctionAppHikariPool");
            DATA_SOURCE = new HikariDataSource(config);
        } catch (Exception e) {
            System.err.println("Failed to initialize database connection pool: " + e.getMessage());
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseUtil() { }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void closeConnectionPool() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}

