package net.auctionapp.server.factories;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.models.items.Item;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class ItemFactory {
    protected void validateBasicData(String title, String description, BigDecimal basePrice) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Item title is required.");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("Item description is required.");
        }
        if (basePrice == null || basePrice.signum() < 0) {
            throw new ValidationException("Item base price must not be negative.");
        }
    }

    public abstract Item createItem(CreateItemRequestMessage message);

    public abstract Item createItem(ResultSet resultSet) throws SQLException;
}
