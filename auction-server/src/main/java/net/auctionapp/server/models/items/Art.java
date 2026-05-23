package net.auctionapp.server.models.items;

import net.auctionapp.common.items.ItemType;

import java.math.BigDecimal;

public class Art extends Item {
    private final String author;
    private final int yearCreated;

    public Art(String id, String title, String description, BigDecimal basePrice, String author, int yearCreated) {
        this(id, title, description, basePrice, author, yearCreated, null);
    }

    public Art(
            String id,
            String title,
            String description,
            BigDecimal basePrice,
            String author,
            int yearCreated,
            String imageUrl
    ) {
        super(id, ItemType.ART, title, description, basePrice, imageUrl);
        this.author = author;
        this.yearCreated = yearCreated;
    }

    public String getAuthor() {
        return author;
    }

    public int getYearCreated() {
        return yearCreated;
    }

    @Override
    public Item copy() {
        return new Art(
                getId(),
                getTitle(),
                getDescription(),
                getBasePrice(),
                author,
                yearCreated,
                getImageUrl()
        );
    }
}
