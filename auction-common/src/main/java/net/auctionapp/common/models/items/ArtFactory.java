package net.auctionapp.common.models.items;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;

import java.math.BigDecimal;
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
}
