package net.auctionapp.server.factories;

import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.models.items.Vehicle;

import java.util.UUID;

public class VehicleFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        return new Vehicle(
                UUID.randomUUID().toString(),
                message.getTitle(),
                message.getDescription(),
                message.getStartingPrice(),
                message.getBrand(),
                message.getModel(),
                message.getYearCreated()
        );
    }
}
