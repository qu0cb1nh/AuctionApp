package net.auctionapp.common.models.items;

import java.math.BigDecimal;

public class Vehicle extends Item {
    private final String brand;
    private final String model;
    private final int yearCreated;

    public Vehicle(String id, String title, String description, BigDecimal basePrice, String brand, String model, int yearCreated) {
        super(id, ItemType.VEHICLE, title, description, basePrice);
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
}
