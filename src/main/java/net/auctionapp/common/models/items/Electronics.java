package net.auctionapp.common.models.items;

import java.math.BigDecimal;

public class Electronics extends Item {
    private final String brand;
    private final String model;
    private final int warrantyMonths;

    public Electronics(String id, String title, String description, BigDecimal basePrice, String brand, String model, int warrantyMonths) {
        super(id, title, description, basePrice);
        this.brand = brand;
        this.model = model;
        this.warrantyMonths = warrantyMonths;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }
}
