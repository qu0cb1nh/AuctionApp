package net.auctionapp.common.messages.types;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

/**
 * A message from the Server to a specific Client to report an error.
 * Inherits from Message and has the type ERROR.
 */
public class ErrorMessage extends Message {
    private String errorMessage;

    // Default constructor is needed for Gson
    public ErrorMessage() {
        super(MessageType.ERROR);
    }

    public ErrorMessage(String errorMessage) {
        super(MessageType.ERROR);
        this.errorMessage = errorMessage;
    }

    // Getters and Setters
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
