package net.auctionapp.server.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.utils.EnvUtil;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final DatabaseConnection INSTANCE = new DatabaseConnection();
    private HikariDataSource dataSource;

    private DatabaseConnection() { }

    public static DatabaseConnection getInstance() {
        return INSTANCE;
    }

    public synchronized void createConnectionPool() { // synchronized in case multithreaded server
        if(dataSource != null) {
            return;
        }
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(EnvUtil.getDatabaseUrl());
            config.setUsername(EnvUtil.getDatabaseUser());
            config.setPassword(EnvUtil.getDatabasePassword());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setPoolName("AuctionAppHikariPool");
            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            throw new DatabaseException("Failed to initialize database connection pool.", e);
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
