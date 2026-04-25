package net.auctionapp.server.factories;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.models.items.Electronics;
import net.auctionapp.common.models.items.Item;
import net.auctionapp.common.models.items.Vehicle;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ElectronicsFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        String title = message.getTitle();
        String description = message.getDescription();
        BigDecimal startingPrice = message.getStartingPrice();
        String brand = message.getBrand();
        String model = message.getModel();
        int warrantyMonths = message.getWarrantyMonths();
        super.validateBasicData(title, description, startingPrice);
        return new Electronics(
                UUID.randomUUID().toString(),
                title,
                description,
                startingPrice,
                brand,
                model,
                warrantyMonths
        );
    }
    @Override
    public Item createItem(ResultSet resultSet) throws SQLException {
        return new Electronics(
                resultSet.getString("item_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getBigDecimal("starting_price"),
                resultSet.getString("brand"),
                resultSet.getString("model"),
                resultSet.getInt("warranty_months")
        );
    }
}
