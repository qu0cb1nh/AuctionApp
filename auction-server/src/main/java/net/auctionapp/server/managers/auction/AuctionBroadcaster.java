package net.auctionapp.server.managers.auction;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.auction.AuctionEndedResponseMessage;
import net.auctionapp.common.messages.auction.AuctionUpdatedResponseMessage;
import net.auctionapp.common.messages.auction.PriceUpdateResponseMessage;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.ServerApp;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.managers.NotificationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AuctionBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionBroadcaster.class);

    private final ConcurrentMap<String, Set<ClientHandler>> auctionSubscribers = new ConcurrentHashMap<>();
    private final AuctionQuery auctionQuery;
    private final NotificationManager notificationManager;
    private final AuctionMutationExecutor auctionMutations;

    public AuctionBroadcaster(
            AuctionQuery auctionQuery,
            NotificationManager notificationManager,
            AuctionMutationExecutor auctionMutations
    ) {
        this.auctionQuery = auctionQuery;
        this.notificationManager = notificationManager;
        this.auctionMutations = auctionMutations;
    }

    public void subscribe(String auctionId, ClientHandler handler) {
        Auction auction = auctionQuery.requireAuction(auctionId);
        Set<ClientHandler> subscribers = auctionSubscribers.computeIfAbsent(auction.getId(), ignored -> ConcurrentHashMap.newKeySet());
        subscribers.add(handler);
        LOGGER.debug("Client subscribed to auction {} updates. Subscriber count: {}.", auction.getId(), subscribers.size());
    }

    public void removeSubscriber(ClientHandler handler) {
        if (handler == null) {
            return;
        }
        for (String auctionId : auctionSubscribers.keySet()) {
            removeSubscriberFrom(auctionId, handler);
        }
    }

    public void unsubscribe(String auctionId, ClientHandler handler) {
        removeSubscriberFrom(auctionId, handler);
    }

    public void broadcastPriceUpdate(Auction auction) {
        PriceUpdateResponseMessage update = auctionMutations.executeWithLock(() -> {
            LOGGER.info("Broadcasting price update for auction {} at price {}.", auction.getId(), auction.getCurrentPrice());
            return new PriceUpdateResponseMessage(
                    auction.getId(),
                    auction.getCurrentPrice(),
                    auctionQuery.displayUsername(auction.getLeadingBidderId()),
                    auction.getEndTime()
            );
        });
        sendToSubscribers(auction.getId(), update);
    }

    public void broadcastAuctionUpdated(Auction auction) {
        AuctionUpdatedResponseMessage update = new AuctionUpdatedResponseMessage(auction.getId());
        LOGGER.info("Broadcasting auction update for auction {} to all connected clients.", auction.getId());
        for (ClientHandler client : ServerApp.getConnectedClients()) {
            client.sendMessage(update);
        }
    }

    public void broadcastAuctionStatusChanged(Auction auction) {
        AuctionEndedResponseMessage update = auctionMutations.executeWithLock(() -> {
            AuctionStatus status = auction.getStatus();
            if (status != AuctionStatus.PAID && status != AuctionStatus.CANCELED) {
                return null;
            }
            LOGGER.info("Broadcasting auction {} ended with status {}.", auction.getId(), status);
            sendAuctionEndedNotifications(auction);
            return new AuctionEndedResponseMessage(
                    auction.getId(),
                    auction.getWinnerBidderId(),
                    auction.getCurrentPrice()
            );
        });
        if (update == null) {
            return;
        }
        sendToSubscribers(auction.getId(), update);
    }

    private void sendToSubscribers(String auctionId, Message message) {
        if (auctionId == null || auctionId.isBlank()) {
            return;
        }
        Set<ClientHandler> subscribers = auctionSubscribers.get(auctionId);
        if (subscribers == null || subscribers.isEmpty()) {
            LOGGER.debug("No subscribers for auction {} message {}.", auctionId, message == null ? null : message.getType());
            return;
        }
        int deliveredCount = 0;
        for (ClientHandler subscriber : subscribers) {
            if (subscriber.sendMessage(message)) {
                deliveredCount++;
            } else {
                removeSubscriberFrom(auctionId, subscriber);
            }
        }
        LOGGER.debug("Delivered {} message to {} subscriber(s) for auction {}.", message.getType(), deliveredCount, auctionId);
    }

    private void removeSubscriberFrom(String auctionId, ClientHandler handler) {
        if (auctionId == null || auctionId.isBlank() || handler == null) {
            return;
        }
        Set<ClientHandler> subscribers = auctionSubscribers.get(auctionId);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(handler);
        LOGGER.debug("Client unsubscribed from auction {} updates. Subscriber count: {}.", auctionId, subscribers.size());
        if (subscribers.isEmpty()) {
            auctionSubscribers.remove(auctionId, subscribers);
        }
    }

    private void sendAuctionEndedNotifications(Auction auction) {
        try {
            notificationManager.sendAuctionEndedNotifications(
                    auction.getId(),
                    auction.getItem().getTitle(),
                    auction.getSellerId(),
                    auction.getWinnerBidderId(),
                    auction.getCurrentPrice()
            );
        } catch (DatabaseException e) {
            LOGGER.warn("Failed to send auction-ended notifications for {}: {}", auction.getId(), e.getMessage());
        }
    }
}
