package net.auctionapp.server.exceptions;

public class NotFoundException extends AuctionAppException {
    public NotFoundException(String message) {
        super(message);
    }
}
