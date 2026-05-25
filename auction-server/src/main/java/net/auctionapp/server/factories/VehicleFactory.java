package net.auctionapp.server.factories;

import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.models.items.Vehicle;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

public class VehicleFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        String title = message.getTitle();
        String description = message.getDescription();
        BigDecimal startingPrice = message.getStartingPrice();
        String brand = message.getBrand();
        String model = message.getModel();
        int yearCreated = message.getYearCreated();
        super.validateBasicData(title, description, startingPrice);
        return new Vehicle(UUID.randomUUID().toString(), title, description, startingPrice, brand, model, yearCreated);
    }

    @Override
    public Item createItem(ResultSet resultSet) throws SQLException {
        return new Vehicle(
                resultSet.getString("item_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getBigDecimal("starting_price"),
                resultSet.getString("brand"),
                resultSet.getString("model"),
                resultSet.getInt("year_created"),
                resultSet.getString("image_url")
        );
    }

    @Override
    public void bindAttributes(PreparedStatement statement, int startIndex, Item item) throws SQLException {
        Vehicle vehicle = (Vehicle) item;
        statement.setString(startIndex, vehicle.getBrand());
        statement.setString(startIndex + 1, vehicle.getModel());
        statement.setNull(startIndex + 2, Types.INTEGER);
        statement.setNull(startIndex + 3, Types.VARCHAR);
        statement.setInt(startIndex + 4, vehicle.getYearCreated());
    }
}
