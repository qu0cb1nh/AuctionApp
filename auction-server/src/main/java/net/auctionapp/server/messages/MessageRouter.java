package net.auctionapp.server.messages;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.AdminGetUsersRequestMessage;
import net.auctionapp.common.messages.types.AdminSetUserBanRequestMessage;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.ClearNotificationsRequestMessage;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.messages.types.DeleteAuctionRequestMessage;
import net.auctionapp.common.messages.types.DepositRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
import net.auctionapp.common.messages.types.GetNotificationsRequestMessage;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.PongMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.UpdateAuctionRequestMessage;
import net.auctionapp.common.messages.types.WithdrawRequestMessage;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.services.AuctionService;
import net.auctionapp.server.services.AuthService;
import net.auctionapp.server.managers.BalanceManager;
import net.auctionapp.server.services.NotificationService;
import net.auctionapp.server.services.UserService;
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
            BalanceManager balanceManager
    ) {
        register(MessageType.PING, Message.class,
                (message, clientHandler) -> clientHandler.sendResponse(new PongMessage(), message));
        register(MessageType.LOGIN_REQUEST, LoginRequestMessage.class, authService::handleLogin);
        register(MessageType.REGISTER_REQUEST, RegisterRequestMessage.class, authService::handleRegister);
        register(MessageType.GET_AUCTION_LIST_REQUEST, GetAuctionListRequestMessage.class,
                auctionService::handleGetAuctionList);
        register(MessageType.GET_AUCTION_DETAILS_REQUEST, GetAuctionDetailsRequestMessage.class,
                auctionService::handleGetAuctionDetails);
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
        register(MessageType.DELETE_AUCTION_REQUEST, DeleteAuctionRequestMessage.class, auctionService::handleDeleteAuction);
        register(MessageType.DEPOSIT_REQUEST, DepositRequestMessage.class, balanceManager::handleDeposit);
        register(MessageType.WITHDRAW_REQUEST, WithdrawRequestMessage.class, balanceManager::handleWithdraw);
    }

    public void dispatch(Message message, ClientHandler clientHandler) {
        if (message == null) {
            return;
        }
        RegisteredCommand<? extends Message> route = routes.get(message.getType());
        if (route == null) {
            LOGGER.warn("Received unsupported message type: {}", message.getType());
            clientHandler.sendResponse(new ErrorMessage("Unsupported message type."), message);
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
