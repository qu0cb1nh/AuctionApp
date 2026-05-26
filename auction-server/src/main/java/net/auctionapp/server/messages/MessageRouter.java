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
import net.auctionapp.common.messages.auction.GetMyActivityRequestMessage;
import net.auctionapp.common.messages.auction.GetMyListingsRequestMessage;
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
import net.auctionapp.server.managers.AuctionManager;
import net.auctionapp.server.managers.AuthManager;
import net.auctionapp.server.managers.NotificationManager;
import net.auctionapp.server.managers.UserManager;
import net.auctionapp.server.managers.WalletManager;
import net.auctionapp.server.managers.WatchListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

public final class MessageRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageRouter.class);

    private final Map<MessageType, RegisteredCommand<? extends Message>> routes =
            new EnumMap<>(MessageType.class);

    public MessageRouter(
            AuthManager authManager,
            AuctionManager auctionManager,
            UserManager userManager,
            NotificationManager notificationManager,
            WalletManager walletManager,
            WatchListManager watchListManager
    ) {
        register(MessageType.PING, Message.class,
                (message, clientHandler) -> clientHandler.sendResponse(new PongResponseMessage(), message));
        register(MessageType.LOGIN_REQUEST, LoginRequestMessage.class, authManager::handleLogin);
        register(MessageType.REGISTER_REQUEST, RegisterRequestMessage.class, authManager::handleRegister);
        register(MessageType.GET_AUCTION_LIST_REQUEST, GetAuctionListRequestMessage.class,
                auctionManager::handleGetAuctionList);
        register(MessageType.GET_AUCTION_DETAILS_REQUEST, GetAuctionDetailsRequestMessage.class,
                auctionManager::handleGetAuctionDetails);
        register(MessageType.GET_MY_ACTIVITY_REQUEST, GetMyActivityRequestMessage.class,
                auctionManager::handleGetMyActivity);
        register(MessageType.GET_MY_LISTINGS_REQUEST, GetMyListingsRequestMessage.class,
                auctionManager::handleGetMyListings);
        register(MessageType.OBSERVE_AUCTION_REQUEST, ObserveAuctionRequestMessage.class,
                auctionManager::handleObserveAuction);
        register(MessageType.GET_NOTIFICATIONS_REQUEST, GetNotificationsRequestMessage.class,
                notificationManager::handleGetNotifications);
        register(MessageType.CREATE_ITEM_REQUEST, CreateItemRequestMessage.class, auctionManager::handleCreateItem);
        register(MessageType.BID_REQUEST, BidRequestMessage.class, auctionManager::handleBidRequest);
        register(MessageType.CLEAR_NOTIFICATIONS_REQUEST, ClearNotificationsRequestMessage.class,
                notificationManager::handleClearNotifications);
        register(MessageType.ADMIN_GET_USERS_REQUEST, AdminGetUsersRequestMessage.class, userManager::handleGetUsers);
        register(MessageType.ADMIN_SET_USER_BAN_REQUEST, AdminSetUserBanRequestMessage.class,
                userManager::handleSetUserBan);
        register(MessageType.UPDATE_AUCTION_REQUEST, UpdateAuctionRequestMessage.class, auctionManager::handleUpdateAuction);
        register(MessageType.CANCEL_AUCTION_REQUEST, CancelAuctionRequestMessage.class, auctionManager::handleCancelAuction);
        register(MessageType.CLOSE_AUCTION_REQUEST, CloseAuctionRequestMessage.class, auctionManager::handleCloseAuction);
        register(MessageType.DEPOSIT_REQUEST, DepositRequestMessage.class, walletManager::handleDeposit);
        register(MessageType.WITHDRAW_REQUEST, WithdrawRequestMessage.class, walletManager::handleWithdraw);
        register(MessageType.GET_WALLET_REQUEST, GetWalletRequestMessage.class, walletManager::handleGetWallet);
        register(MessageType.GET_WATCH_LIST_REQUEST, GetWatchListRequestMessage.class,
                watchListManager::handleGetWatchList);
        register(MessageType.UPDATE_WATCH_LIST_REQUEST, UpdateWatchListRequestMessage.class,
                watchListManager::handleUpdateWatchList);
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
