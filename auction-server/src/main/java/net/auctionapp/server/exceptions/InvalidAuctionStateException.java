package net.auctionapp.server.exceptions;

public class InvalidAuctionStateException extends RuntimeException {
    public InvalidAuctionStateException(String message) {
        super(message);
    }
}
