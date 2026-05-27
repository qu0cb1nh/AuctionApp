package net.auctionapp.common.messages.auction;

import net.auctionapp.common.dto.ListingSummaryDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class MyListingsResponseMessage extends Message {
    private List<ListingSummaryDto> listings;

    public MyListingsResponseMessage() {
        super(MessageType.MY_LISTINGS_RESPONSE);
    }

    public MyListingsResponseMessage(List<ListingSummaryDto> listings) {
        super(MessageType.MY_LISTINGS_RESPONSE);
        this.listings = listings == null ? List.of() : List.copyOf(listings);
    }

    public List<ListingSummaryDto> getListings() {
        return listings == null ? List.of() : List.copyOf(listings);
    }
}
