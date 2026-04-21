package net.auctionapp.common.models;

public abstract class Entity {
    private final String id;

    public Entity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
