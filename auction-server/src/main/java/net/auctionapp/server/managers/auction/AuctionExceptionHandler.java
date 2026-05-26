package net.auctionapp.server.managers.auction;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.AuthorizationException;
import net.auctionapp.server.exceptions.ImageStorageException;
import net.auctionapp.server.exceptions.InsufficientFundsException;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuctionExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionExceptionHandler.class);

    private AuctionExceptionHandler() {
    }

    public static String clientMessage(RuntimeException exception, String operation, String failureMessage) {
        if (exception instanceof AuthenticationException
                || exception instanceof AuthorizationException
                || exception instanceof InsufficientFundsException
                || exception instanceof InvalidAuctionStateException
                || exception instanceof NotFoundException
                || exception instanceof ValidationException) {
            return exception.getMessage();
        }
        LOGGER.warn("{} failed: {}", operation, exception.getMessage(), exception);
        return exception instanceof ImageStorageException ? "Unable to store auction image." : failureMessage;
    }
}
