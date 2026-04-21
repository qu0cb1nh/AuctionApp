package net.auctionapp.server.exceptions;

public class InvalidBidException extends AuctionAppException {
    public InvalidBidException(String message) {
        super(message);
    }
}
