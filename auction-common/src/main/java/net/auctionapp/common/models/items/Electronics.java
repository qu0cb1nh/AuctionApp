package net.auctionapp.common.models.items;

import java.math.BigDecimal;

public class Electronics extends Item {
    private final String brand;
    private final String model;
    private final int warrantyMonths;

    public Electronics(String id, String title, String description, BigDecimal basePrice, String brand, String model, int warrantyMonths) {
        this(id, title, description, basePrice, brand, model, warrantyMonths, null);
    }

    public Electronics(
            String id,
            String title,
            String description,
            BigDecimal basePrice,
            String brand,
            String model,
            int warrantyMonths,
            String imageUrl
    ) {
        super(id, ItemType.ELECTRONICS, title, description, basePrice, imageUrl);
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
