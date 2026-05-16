package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

public class CreateItemResultMessage extends Message {
    private String auctionId;
    private String title;
    private String imageUrl;
    private String message;

    public CreateItemResultMessage() {
        super(MessageType.CREATE_ITEM_SUCCESS);
    }

    public CreateItemResultMessage(String auctionId, String title, String message) {
        this(auctionId, title, null, message);
    }

    public CreateItemResultMessage(String auctionId, String title, String imageUrl, String message) {
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
