package net.auctionapp.server.models.items;

import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;

public class Vehicle extends Item {
    private final String brand;
    private final String model;
    private final int yearCreated;

    public Vehicle(String id, String title, String description, BigDecimal basePrice, String brand, String model, int yearCreated) {
        this(id, title, description, basePrice, brand, model, yearCreated, null);
    }

    public Vehicle(
            String id,
            String title,
            String description,
            BigDecimal basePrice,
            String brand,
            String model,
            int yearCreated,
            String imageUrl
    ) {
        super(id, ItemType.VEHICLE, title, description, basePrice, imageUrl);
        this.brand = brand;
        this.model = model;
        this.yearCreated = yearCreated;
    }

    public String getModel() {
        return model;
    }

    public int getYearCreated() {
        return yearCreated;
    }

    public String getBrand() {
        return brand;
    }

    @Override
    public Item copy() {
        return new Vehicle(
                getId(),
                getTitle(),
                getDescription(),
                getBasePrice(),
                brand,
                model,
                yearCreated,
                getImageUrl()
        );
    }
}
