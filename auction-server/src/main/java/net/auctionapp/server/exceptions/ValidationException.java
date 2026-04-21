package net.auctionapp.server.exceptions;

public class ValidationException extends AuctionAppException {
    public ValidationException(String message) {
        super(message);
    }
}
