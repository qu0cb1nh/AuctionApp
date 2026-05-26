package net.auctionapp.server.factories;

import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.server.models.items.Art;
import net.auctionapp.server.models.items.Item;

import java.util.UUID;

public class ArtFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        return new Art(
                UUID.randomUUID().toString(),
                message.getTitle(),
                message.getDescription(),
                message.getStartingPrice(),
                message.getAuthor(),
                message.getYearCreated()
        );
    }
}
