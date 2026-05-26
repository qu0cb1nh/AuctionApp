package net.auctionapp.server.factories;

import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.server.models.items.Electronics;
import net.auctionapp.server.models.items.Item;

import java.util.UUID;

public class ElectronicsFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        return new Electronics(
                UUID.randomUUID().toString(),
                message.getTitle(),
                message.getDescription(),
                message.getStartingPrice(),
                message.getBrand(),
                message.getModel(),
                message.getWarrantyMonths()
        );
    }
}
