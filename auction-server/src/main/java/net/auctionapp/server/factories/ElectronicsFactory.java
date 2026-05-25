package net.auctionapp.server.factories;

import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.server.models.items.Electronics;
import net.auctionapp.server.models.items.Item;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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
                resultSet.getInt("warranty_months"),
                resultSet.getString("image_url")
        );
    }

    @Override
    public void bindAttributes(PreparedStatement statement, int startIndex, Item item) throws SQLException {
        Electronics electronics = (Electronics) item;
        statement.setString(startIndex, electronics.getBrand());
        statement.setString(startIndex + 1, electronics.getModel());
        statement.setInt(startIndex + 2, electronics.getWarrantyMonths());
        statement.setNull(startIndex + 3, Types.VARCHAR);
        statement.setNull(startIndex + 4, Types.INTEGER);
    }
}
