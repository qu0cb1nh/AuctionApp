package net.auctionapp.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.admin.*;
import net.auctionapp.common.messages.auction.*;
import net.auctionapp.common.messages.auth.*;
import net.auctionapp.common.messages.notification.*;
import net.auctionapp.common.messages.system.*;
import net.auctionapp.common.messages.wallet.*;
import net.auctionapp.common.messages.watchlist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public final class JsonUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtil.class);

    private JsonUtil() { }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
            .create();

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    public static Message fromJson(String json) {
        try {
            JsonObject jsonObject = GSON.fromJson(json, JsonObject.class);

            if (jsonObject == null || !jsonObject.has("type")) {
                System.err.println("Invalid JSON or missing 'type' field: " + json);
                return null;
            }

            String typeString = jsonObject.get("type").getAsString();
            MessageType type = MessageType.valueOf(typeString);

            return switch (type) {
                case LOGIN_REQUEST -> GSON.fromJson(json, LoginRequestMessage.class);
                case REGISTER_REQUEST -> GSON.fromJson(json, RegisterRequestMessage.class);
                case GET_AUCTION_LIST_REQUEST -> GSON.fromJson(json, GetAuctionListRequestMessage.class);
                case GET_AUCTION_DETAILS_REQUEST -> GSON.fromJson(json, GetAuctionDetailsRequestMessage.class);
                case GET_MY_ACTIVITY_REQUEST -> GSON.fromJson(json, GetMyActivityRequestMessage.class);
                case GET_MY_LISTINGS_REQUEST -> GSON.fromJson(json, GetMyListingsRequestMessage.class);
                case OBSERVE_AUCTION_REQUEST -> GSON.fromJson(json, ObserveAuctionRequestMessage.class);
                case GET_NOTIFICATIONS_REQUEST -> GSON.fromJson(json, GetNotificationsRequestMessage.class);
                case GET_WALLET_REQUEST -> GSON.fromJson(json, GetWalletRequestMessage.class);
                case GET_WATCH_LIST_REQUEST -> GSON.fromJson(json, GetWatchListRequestMessage.class);
                case UPDATE_WATCH_LIST_REQUEST -> GSON.fromJson(json, UpdateWatchListRequestMessage.class);
                case CREATE_ITEM_REQUEST -> GSON.fromJson(json, CreateItemRequestMessage.class);
                case CLEAR_NOTIFICATIONS_REQUEST -> GSON.fromJson(json, ClearNotificationsRequestMessage.class);
                case GET_USER_LIST_REQUEST -> GSON.fromJson(json, GetUserListRequestMessage.class);
                case SET_USER_BAN_REQUEST -> GSON.fromJson(json, SetUserBanRequestMessage.class);
                case UPDATE_AUCTION_REQUEST -> GSON.fromJson(json, UpdateAuctionRequestMessage.class);
                case CANCEL_AUCTION_REQUEST -> GSON.fromJson(json, CancelAuctionRequestMessage.class);
                case CLOSE_AUCTION_REQUEST -> GSON.fromJson(json, CloseAuctionRequestMessage.class);
                case CANCEL_BIDS_REQUEST -> GSON.fromJson(json, CancelBidsRequestMessage.class);
                case LOGIN_SUCCESS, LOGIN_FAILURE -> GSON.fromJson(json, LoginResponseMessage.class);
                case FORCED_LOGOUT -> GSON.fromJson(json, ForcedLogoutResponseMessage.class);
                case REGISTER_SUCCESS, REGISTER_FAILURE -> GSON.fromJson(json, RegisterResponseMessage.class);
                case AUCTION_LIST_RESPONSE -> GSON.fromJson(json, AuctionListResponseMessage.class);
                case AUCTION_DETAILS_RESPONSE -> GSON.fromJson(json, AuctionDetailsResponseMessage.class);
                case MY_ACTIVITY_RESPONSE -> GSON.fromJson(json, MyActivityResponseMessage.class);
                case MY_LISTINGS_RESPONSE -> GSON.fromJson(json, MyListingsResponseMessage.class);
                case NOTIFICATIONS_RESPONSE -> GSON.fromJson(json, NotificationsResponseMessage.class);
                case WATCH_LIST_RESPONSE -> GSON.fromJson(json, WatchListResponseMessage.class);
                case WATCH_LIST_CHANGED -> GSON.fromJson(json, WatchListChangedResponseMessage.class);
                case CREATE_ITEM_SUCCESS -> GSON.fromJson(json, CreateItemResponseMessage.class);
                case GET_USER_LIST_RESPONSE -> GSON.fromJson(json, GetUserListResponseMessage.class);
                case SET_USER_BAN_RESPONSE -> GSON.fromJson(json, SetUserBanResponseMessage.class);
                case AUCTION_ACTION_SUCCESS -> GSON.fromJson(json, AuctionActionResponseMessage.class);
                case AUCTION_UPDATED -> GSON.fromJson(json, AuctionUpdatedResponseMessage.class);
                case BID_REQUEST -> GSON.fromJson(json, BidRequestMessage.class);
                case BID_ACCEPTED, BID_REJECTED -> GSON.fromJson(json, BidResponseMessage.class);
                case PRICE_UPDATE -> GSON.fromJson(json, PriceUpdateResponseMessage.class);
                case AUCTION_ENDED -> GSON.fromJson(json, AuctionEndedResponseMessage.class);
                case NOTIFICATION -> GSON.fromJson(json, NotificationResponseMessage.class);
                case ERROR -> GSON.fromJson(json, ErrorResponseMessage.class);
                case PING -> GSON.fromJson(json, PingRequestMessage.class);
                case PONG -> GSON.fromJson(json, PongResponseMessage.class);
                case DEPOSIT_REQUEST -> GSON.fromJson(json, DepositRequestMessage.class);
                case WITHDRAW_REQUEST -> GSON.fromJson(json, WithdrawRequestMessage.class);
                case WALLET_RESPONSE, BALANCE_UPDATE -> GSON.fromJson(json, WalletResponseMessage.class);
            };
        } catch (Exception e) {
            LOGGER.error("Error while parsing JSON object", e);
            return null;
        }
    }

    private static final class LocalDateTimeTypeAdapter extends TypeAdapter<LocalDateTime> {

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.toString());
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String value = in.nextString();
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ex) {
                throw new JsonParseException("Invalid LocalDateTime value: " + value, ex);
            }
        }
    }
}
