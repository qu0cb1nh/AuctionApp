package net.auctionapp.common.models.items;

import net.auctionapp.common.models.Entity;

import java.math.BigDecimal;

public abstract class Item extends Entity {
    private String title;
    private String description;
    private BigDecimal basePrice;

    public Item(String id,String title, String description, BigDecimal basePrice) {
        super(id);
        this.title = title;
        this.description = description;
        this.basePrice = basePrice;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void updateDetails(String title, String description, BigDecimal basePrice) {
        this.title = title;
        this.description = description;
        this.basePrice = basePrice;
    }
}
