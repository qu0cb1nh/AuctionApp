package net.auctionapp.server.utils;

import net.auctionapp.common.items.ItemType;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.models.items.Art;
import net.auctionapp.server.models.items.Electronics;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.models.items.Vehicle;
import net.auctionapp.server.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public final class JdbcUtil {
    private JdbcUtil() {
    }

    public static void ensureTable(DatabaseConnection databaseConnection, String query, String tableName) {
        try (Connection connection = databaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create " + tableName + " table.", e);
        }
    }

    public static void setNullableString(PreparedStatement statement, int parameterIndex, String value)
            throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(parameterIndex, Types.VARCHAR);
            return;
        }
        statement.setString(parameterIndex, value);
    }

    public static Item mapItem(ResultSet resultSet) throws SQLException {
        ItemType type = ItemType.valueOf(resultSet.getString("item_type"));
        return switch (type) {
            case ART -> new Art(
                    resultSet.getString("item_id"),
                    resultSet.getString("title"),
                    resultSet.getString("description"),
                    resultSet.getBigDecimal("starting_price"),
                    resultSet.getString("author"),
                    resultSet.getInt("year_created"),
                    resultSet.getString("image_url")
            );
            case ELECTRONICS -> new Electronics(
                    resultSet.getString("item_id"),
                    resultSet.getString("title"),
                    resultSet.getString("description"),
                    resultSet.getBigDecimal("starting_price"),
                    resultSet.getString("brand"),
                    resultSet.getString("model"),
                    resultSet.getInt("warranty_months"),
                    resultSet.getString("image_url")
            );
            case VEHICLE -> new Vehicle(
                    resultSet.getString("item_id"),
                    resultSet.getString("title"),
                    resultSet.getString("description"),
                    resultSet.getBigDecimal("starting_price"),
                    resultSet.getString("brand"),
                    resultSet.getString("model"),
                    resultSet.getInt("year_created"),
                    resultSet.getString("image_url")
            );
        };
    }

    public static void bindItemAttributes(PreparedStatement statement, int startIndex, Item item)
            throws SQLException {
        switch (item.getType()) {
            case ART -> {
                Art art = (Art) item;
                statement.setNull(startIndex, Types.VARCHAR);
                statement.setNull(startIndex + 1, Types.VARCHAR);
                statement.setNull(startIndex + 2, Types.INTEGER);
                statement.setString(startIndex + 3, art.getAuthor());
                statement.setInt(startIndex + 4, art.getYearCreated());
            }
            case ELECTRONICS -> {
                Electronics electronics = (Electronics) item;
                statement.setString(startIndex, electronics.getBrand());
                statement.setString(startIndex + 1, electronics.getModel());
                statement.setInt(startIndex + 2, electronics.getWarrantyMonths());
                statement.setNull(startIndex + 3, Types.VARCHAR);
                statement.setNull(startIndex + 4, Types.INTEGER);
            }
            case VEHICLE -> {
                Vehicle vehicle = (Vehicle) item;
                statement.setString(startIndex, vehicle.getBrand());
                statement.setString(startIndex + 1, vehicle.getModel());
                statement.setNull(startIndex + 2, Types.INTEGER);
                statement.setNull(startIndex + 3, Types.VARCHAR);
                statement.setInt(startIndex + 4, vehicle.getYearCreated());
            }
        }
    }
}
