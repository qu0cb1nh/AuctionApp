package net.auctionapp.server.factories;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.models.items.Item;
import net.auctionapp.common.models.items.Vehicle;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
                resultSet.getInt("year_created")
        );
    }
}
