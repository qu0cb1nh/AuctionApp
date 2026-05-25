package net.auctionapp.common.messages.auction;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class CreateItemResponseMessage extends Message {
    private String auctionId;
    private String title;
    private String imageUrl;
    private String message;

    public CreateItemResponseMessage() {
        super(MessageType.CREATE_ITEM_SUCCESS);
    }

    public CreateItemResponseMessage(String auctionId, String title, String message) {
        this(auctionId, title, null, message);
    }

    public CreateItemResponseMessage(String auctionId, String title, String imageUrl, String message) {
        super(MessageType.CREATE_ITEM_SUCCESS);
        this.auctionId = auctionId;
        this.title = title;
        this.imageUrl = imageUrl;
        this.message = message;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getTitle() {
        return title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getMessage() {
        return message;
    }
}
