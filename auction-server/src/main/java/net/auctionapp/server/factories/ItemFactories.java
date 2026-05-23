package net.auctionapp.server.factories;

import net.auctionapp.common.items.ItemType;

public final class ItemFactories {
    private ItemFactories() {
    }

    public static ItemFactory forType(ItemType type) {
        if (type == null) {
            throw new IllegalArgumentException("Item type is required.");
        }
        return switch (type) {
            case ART -> new ArtFactory();
            case ELECTRONICS -> new ElectronicsFactory();
            case VEHICLE -> new VehicleFactory();
        };
    }
}
