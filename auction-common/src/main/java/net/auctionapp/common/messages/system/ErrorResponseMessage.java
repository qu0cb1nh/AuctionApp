package net.auctionapp.common.messages.system;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

/**
 * A message from the Server to a specific Client to report an error.
 * Inherits from Message and has the type ERROR.
 */
public class ErrorResponseMessage extends Message {
    private String errorMessage;

    // Default constructor is needed for Gson
    public ErrorResponseMessage() {
        super(MessageType.ERROR);
    }

    public ErrorResponseMessage(String errorMessage) {
        super(MessageType.ERROR);
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
