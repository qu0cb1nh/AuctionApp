package net.auctionapp.server.managers.auction;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes auction mutations that update aggregate and persistence state together.
 */
public final class AuctionMutationExecutor {
    private final ReentrantLock auctionMutationLock = new ReentrantLock();

    public <T> T executeWithLock(Supplier<T> operation) {
        auctionMutationLock.lock();
        try {
            return operation.get();
        } finally {
            auctionMutationLock.unlock();
        }
    }

    public void executeWithLock(Runnable operation) {
        executeWithLock(() -> {
            operation.run();
            return null;
        });
    }
}
