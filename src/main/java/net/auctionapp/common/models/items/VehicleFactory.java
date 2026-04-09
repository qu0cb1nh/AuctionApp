package net.auctionapp.common.models.items;

import net.auctionapp.common.messages.types.CreateItemRequestMessage;

import java.math.BigDecimal;
import java.util.UUID;

public class VehicleFactory extends ItemFactory {
    @Override
    public Item createItem(CreateItemRequestMessage message) {
        String title = message.getTitle();
        String description = message.getDescription();
        BigDecimal basePrice = message.getBasePrice();
        String brand = message.getBrand();
        String model = message.getModel();
        int yearCreated = message.getYearCreated();
        super.validateBasicData(title, description, basePrice);
        return new Vehicle(UUID.randomUUID().toString(), title, description, basePrice, brand, model, yearCreated);
    }
}
