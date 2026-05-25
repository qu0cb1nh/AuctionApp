package net.auctionapp.server.messages;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.admin.AdminGetUsersRequestMessage;
import net.auctionapp.common.messages.admin.AdminSetUserBanRequestMessage;
import net.auctionapp.common.messages.auction.BidRequestMessage;
import net.auctionapp.common.messages.auction.CancelAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CloseAuctionRequestMessage;
import net.auctionapp.common.messages.auction.CreateItemRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.auction.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.auction.ObserveAuctionRequestMessage;
import net.auctionapp.common.messages.auction.UpdateAuctionRequestMessage;
import net.auctionapp.common.messages.auth.LoginRequestMessage;
import net.auctionapp.common.messages.auth.RegisterRequestMessage;
import net.auctionapp.common.messages.notification.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.notification.GetNotificationsRequestMessage;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.system.PongResponseMessage;
import net.auctionapp.common.messages.wallet.DepositRequestMessage;
import net.auctionapp.common.messages.wallet.GetWalletRequestMessage;
import net.auctionapp.common.messages.wallet.WithdrawRequestMessage;
import net.auctionapp.common.messages.watchlist.GetWatchListRequestMessage;
import net.auctionapp.common.messages.watchlist.UpdateWatchListRequestMessage;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.services.AuctionService;
import net.auctionapp.server.services.AuthService;
import net.auctionapp.server.services.NotificationService;
import net.auctionapp.server.services.UserService;
import net.auctionapp.server.services.WalletService;
import net.auctionapp.server.services.WatchListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

public final class MessageRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageRouter.class);

    private final Map<MessageType, RegisteredCommand<? extends Message>> routes =
            new EnumMap<>(MessageType.class);

    public MessageRouter(
            AuthService authService,
            AuctionService auctionService,
            UserService userService,
            NotificationService notificationService,
            WalletService walletService,
            WatchListService watchListService
    ) {
        register(MessageType.PING, Message.class,
                (message, clientHandler) -> clientHandler.sendResponse(new PongResponseMessage(), message));
        register(MessageType.LOGIN_REQUEST, LoginRequestMessage.class, authService::handleLogin);
        register(MessageType.REGISTER_REQUEST, RegisterRequestMessage.class, authService::handleRegister);
        register(MessageType.GET_AUCTION_LIST_REQUEST, GetAuctionListRequestMessage.class,
                auctionService::handleGetAuctionList);
        register(MessageType.GET_AUCTION_DETAILS_REQUEST, GetAuctionDetailsRequestMessage.class,
                auctionService::handleGetAuctionDetails);
        register(MessageType.OBSERVE_AUCTION_REQUEST, ObserveAuctionRequestMessage.class,
                auctionService::handleObserveAuction);
        register(MessageType.GET_NOTIFICATIONS_REQUEST, GetNotificationsRequestMessage.class,
                notificationService::handleGetNotifications);
        register(MessageType.CREATE_ITEM_REQUEST, CreateItemRequestMessage.class, auctionService::handleCreateItem);
        register(MessageType.BID_REQUEST, BidRequestMessage.class, auctionService::handleBidRequest);
        register(MessageType.CLEAR_NOTIFICATIONS_REQUEST, ClearNotificationsRequestMessage.class,
                notificationService::handleClearNotifications);
        register(MessageType.ADMIN_GET_USERS_REQUEST, AdminGetUsersRequestMessage.class, userService::handleGetUsers);
        register(MessageType.ADMIN_SET_USER_BAN_REQUEST, AdminSetUserBanRequestMessage.class,
                userService::handleSetUserBan);
        register(MessageType.UPDATE_AUCTION_REQUEST, UpdateAuctionRequestMessage.class, auctionService::handleUpdateAuction);
        register(MessageType.CANCEL_AUCTION_REQUEST, CancelAuctionRequestMessage.class, auctionService::handleCancelAuction);
        register(MessageType.CLOSE_AUCTION_REQUEST, CloseAuctionRequestMessage.class, auctionService::handleCloseAuction);
        register(MessageType.DEPOSIT_REQUEST, DepositRequestMessage.class, walletService::handleDeposit);
        register(MessageType.WITHDRAW_REQUEST, WithdrawRequestMessage.class, walletService::handleWithdraw);
        register(MessageType.GET_WALLET_REQUEST, GetWalletRequestMessage.class, walletService::handleGetWallet);
        register(MessageType.GET_WATCH_LIST_REQUEST, GetWatchListRequestMessage.class,
                watchListService::handleGetWatchList);
        register(MessageType.UPDATE_WATCH_LIST_REQUEST, UpdateWatchListRequestMessage.class,
                watchListService::handleUpdateWatchList);
    }

    public void dispatch(Message message, ClientHandler clientHandler) {
        if (message == null) {
            return;
        }
        RegisteredCommand<? extends Message> route = routes.get(message.getType());
        if (route == null) {
            LOGGER.warn("Received unsupported message type: {}", message.getType());
            clientHandler.sendResponse(new ErrorResponseMessage("Unsupported message type."), message);
            return;
        }
        route.execute(message, clientHandler);
    }

    private <T extends Message> void register(
            MessageType messageType,
            Class<T> messageClass,
            MessageCommand<T> command
    ) {
        routes.put(messageType, new RegisteredCommand<>(messageClass, command));
    }

    private record RegisteredCommand<T extends Message>(
            Class<T> messageClass,
            MessageCommand<T> command
    ) {
        void execute(Message message, ClientHandler clientHandler) {
            command.execute(messageClass.cast(message), clientHandler);
        }
    }
}
