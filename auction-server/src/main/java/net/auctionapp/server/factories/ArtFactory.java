package net.auctionapp.server.factories;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.server.models.items.Art;
import net.auctionapp.server.models.items.Item;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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
                resultSet.getInt("year_created"),
                resultSet.getString("image_url")
        );
    }

    @Override
    public void bindAttributes(PreparedStatement statement, int startIndex, Item item) throws SQLException {
        Art art = (Art) item;
        statement.setNull(startIndex, Types.VARCHAR);
        statement.setNull(startIndex + 1, Types.VARCHAR);
        statement.setNull(startIndex + 2, Types.INTEGER);
        statement.setString(startIndex + 3, art.getAuthor());
        statement.setInt(startIndex + 4, art.getYearCreated());
    }
}
