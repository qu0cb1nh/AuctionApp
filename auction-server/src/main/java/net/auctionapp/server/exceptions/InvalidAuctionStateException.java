package net.auctionapp.server.exceptions;

public class InvalidAuctionStateException extends AuctionAppException {
    public InvalidAuctionStateException(String message) {
        super(message);
    }
}
