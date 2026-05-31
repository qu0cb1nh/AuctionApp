package net.auctionapp.server.managers.auction;

import net.auctionapp.server.models.auction.Auction;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

    public <T> T executeWithLock(
            Auction auction,
            Function<Auction, T> mutation,
            BiConsumer<Auction, T> persist,
            BiConsumer<Auction, T> afterApply
    ) {
        return executeWithLock(() -> {
            Auction candidate = auction.snapshotCopy();
            T result = mutation.apply(candidate);
            if (persist != null) {
                persist.accept(candidate, result);
            }
            auction.applySnapshot(candidate);
            if (afterApply != null) {
                afterApply.accept(auction, result);
            }
            return result;
        });
    }

    public void executeWithLock(
            Auction auction,
            Consumer<Auction> mutation,
            Consumer<Auction> persist
    ) {
        executeWithLock(
                auction,
                candidate -> {
                    mutation.accept(candidate);
                    return null;
                },
                (candidate, ignored) -> persist.accept(candidate),
                null
        );
    }
}
