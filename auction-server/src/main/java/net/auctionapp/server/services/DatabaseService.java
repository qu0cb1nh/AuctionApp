package net.auctionapp.server.services;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.auctionapp.common.utils.ConfigUtil;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseService {
    private static final DatabaseService INSTANCE = new DatabaseService();
    private HikariDataSource dataSource;

    private DatabaseService() { }

    public static DatabaseService getInstance() {
        return INSTANCE;
    }

    public synchronized void createConnectionPool() { // synchronized in case multithreaded server
        if(dataSource != null) {
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
            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            System.err.println("Failed to initialize database connection pool: " + e.getMessage());
            throw new ExceptionInInitializerError(e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Data source is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    public synchronized void closeConnectionPool() { // synchronized in case multithreaded server
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

