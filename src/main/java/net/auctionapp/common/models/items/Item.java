package net.auctionapp.common.models.items;

import net.auctionapp.common.models.Entity;

import java.math.BigDecimal;

public abstract class Item extends Entity {
    private final String title;
    private final String description;
    private final BigDecimal basePrice;

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
}
