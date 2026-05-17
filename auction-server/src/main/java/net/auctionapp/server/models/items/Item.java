package net.auctionapp.server.models.items;

import net.auctionapp.common.items.ItemType;

import net.auctionapp.server.models.Entity;

import java.math.BigDecimal;

public abstract class Item extends Entity {
    private ItemType type;
    private String title;
    private String description;
    private BigDecimal basePrice;
    private String imageUrl;

    public Item(String id, ItemType type, String title, String description, BigDecimal basePrice) {
        this(id, type, title, description, basePrice, null);
    }

    public Item(String id, ItemType type, String title, String description, BigDecimal basePrice, String imageUrl) {
        super(id);
        this.type = type;
        this.title = title;
        this.description = description;
        this.basePrice = basePrice;
        this.imageUrl = imageUrl;
    }

    public ItemType getType() {
        return type;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void updateDetails(String title, String description, BigDecimal basePrice) {
        this.title = title;
        this.description = description;
        this.basePrice = basePrice;
    }
}
