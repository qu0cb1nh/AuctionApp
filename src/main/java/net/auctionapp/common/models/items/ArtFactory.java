package net.auctionapp.common.models.items;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;

import java.math.BigDecimal;
import java.util.UUID;

public class ArtFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        String title = message.getTitle();
        String description = message.getDescription();
        BigDecimal basePrice = message.getBasePrice();
        String author = message.getAuthor();
        int yearCreated = message.getYearCreated();
        super.validateBasicData(title, description, basePrice);
        return new Art(UUID.randomUUID().toString(), title, description, basePrice, author, yearCreated);
    }
}
