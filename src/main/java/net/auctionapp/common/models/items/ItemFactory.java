package net.auctionapp.common.models.items;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.server.exceptions.ValidationException;

import java.math.BigDecimal;

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
}
