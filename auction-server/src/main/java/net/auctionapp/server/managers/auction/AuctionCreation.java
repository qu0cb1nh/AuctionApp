package net.auctionapp.server.managers.auction;

import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemResponseMessage;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.InvalidAuctionStateException;
import net.auctionapp.server.factories.ArtFactory;
import net.auctionapp.server.factories.ElectronicsFactory;
import net.auctionapp.server.factories.VehicleFactory;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.services.CloudinaryImageService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public final class AuctionCreation {
    private final ConcurrentMap<String, Auction> auctions;
    private final AuctionMutationExecutor auctionMutations;
    private final AuthManager authManager;
    private final CloudinaryImageService cloudinaryImageService;
    private final AuctionPersistence auctionPersistence;
    private final Clock clock;

    public AuctionCreation(
            ConcurrentMap<String, Auction> auctions,
            AuctionMutationExecutor auctionMutations,
            AuthManager authManager,
            CloudinaryImageService cloudinaryImageService,
            AuctionPersistence auctionPersistence,
            Clock clock
    ) {
        this.auctions = auctions;
        this.auctionMutations = auctionMutations;
        this.authManager = authManager;
        this.cloudinaryImageService = cloudinaryImageService;
        this.auctionPersistence = auctionPersistence;
        this.clock = clock;
    }

    public CreateItemResponseMessage createAuction(CreateItemRequestMessage request, String sellerId) {
        CloudinaryImageService.UploadedImage uploadedImage = null;
        try {
            validateCreateAuctionRequest(request);
            Item item = createItem(request);
            uploadedImage = cloudinaryImageService.uploadAuctionItemImage(request);
            item.setImageUrl(uploadedImage == null ? null : uploadedImage.url());
            Auction auction = openAuction(
                    sellerId,
                    item,
                    request.getStartingPrice(),
                    request.getMinimumBidIncrement(),
                    request.getStartTime(),
                    request.getEndTime()
            );
            return new CreateItemResponseMessage(
                    auction.getId(),
                    auction.getItem().getTitle(),
                    auction.getItem().getImageUrl(),
                    "Auction created successfully."
            );
        } catch (RuntimeException e) {
            cloudinaryImageService.deleteAuctionItemImage(uploadedImage);
            throw e;
        }
    }

    private Auction openAuction(
            String sellerId,
            Item item,
            BigDecimal startingPrice,
            BigDecimal minimumBidIncrement,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return auctionMutations.executeWithLock(() -> {
            var seller = authManager.requireActiveUserById(StringUtil.normalizeString(sellerId));
            Auction auction = new Auction(
                    UUID.randomUUID().toString(),
                    seller.getId(),
                    startTime,
                    endTime,
                    item,
                    startingPrice,
                    minimumBidIncrement,
                    clock
            );

            if (auctions.putIfAbsent(auction.getId(), auction) != null) {
                throw new InvalidAuctionStateException("Auction is already open.");
            }
            try {
                auctionPersistence.persistAuction(auction);
            } catch (RuntimeException e) {
                auctions.remove(auction.getId(), auction);
                throw e;
            }
            return auction;
        });
    }

    private void validateCreateAuctionRequest(CreateItemRequestMessage message) {
        if (message == null || message.getItemType() == null) {
            throw new ValidationException("Item type is required.");
        }
        requireText(message.getTitle(), "Item title");
        requireText(message.getDescription(), "Item description");
        if (message.getStartingPrice() == null || message.getStartingPrice().signum() < 0) {
            throw new ValidationException("Starting price must not be negative.");
        }
        if (message.getMinimumBidIncrement() == null || message.getMinimumBidIncrement().signum() <= 0) {
            throw new ValidationException("Minimum bid increment must be greater than zero.");
        }
        if (message.getStartTime() == null
                || message.getEndTime() == null
                || !message.getEndTime().isAfter(message.getStartTime())) {
            throw new ValidationException("Auction end time must be after start time.");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
    }

    private Item createItem(CreateItemRequestMessage message) {
        return switch (message.getItemType()) {
            case ART -> new ArtFactory().createItem(message);
            case ELECTRONICS -> new ElectronicsFactory().createItem(message);
            case VEHICLE -> new VehicleFactory().createItem(message);
        };
    }
}
