package net.auctionapp.server.factories;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.models.items.Art;
import net.auctionapp.common.models.items.Item;
import net.auctionapp.common.models.items.Vehicle;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ArtFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        String title = message.getTitle();
        String description = message.getDescription();
        BigDecimal startingPrice = message.getStartingPrice();
        String author = message.getAuthor();
        int yearCreated = message.getYearCreated();
        super.validateBasicData(title, description, startingPrice);
        return new Art(UUID.randomUUID().toString(), title, description, startingPrice, author, yearCreated);
    }

    @Override
    public Item createItem(ResultSet resultSet) throws SQLException {
        return new Art(
                resultSet.getString("item_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getBigDecimal("starting_price"),
                resultSet.getString("author"),
                resultSet.getInt("year_created")
        );
    }
}
