package net.auctionapp.common.models.items;

import java.math.BigDecimal;

public class Art extends Item {
    private final String author;
    private final int yearCreated;

    public Art(String id, String title, String description, BigDecimal basePrice, String author, int yearCreated) {
        super(id, ItemType.ART, title, description, basePrice);
        this.author = author;
        this.yearCreated = yearCreated;
    }

    public String getAuthor() {
        return author;
    }

    public int getYearCreated() {
        return yearCreated;
    }
}
