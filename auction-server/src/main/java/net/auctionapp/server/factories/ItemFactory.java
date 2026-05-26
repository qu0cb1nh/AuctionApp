package net.auctionapp.server.factories;

import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.server.models.items.Item;

public abstract class ItemFactory {
    public abstract Item createItem(CreateItemRequestMessage message);
}
