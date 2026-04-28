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
import net.auctionapp.common.messages.types.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public final class JsonUtil {

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
                case GET_NOTIFICATIONS_REQUEST -> GSON.fromJson(json, GetNotificationsRequestMessage.class);
                case CREATE_ITEM_REQUEST -> GSON.fromJson(json, CreateItemRequestMessage.class);
                case MARK_NOTIFICATION_READ_REQUEST -> GSON.fromJson(json, MarkNotificationReadRequestMessage.class);
                case CLEAR_NOTIFICATIONS_REQUEST -> GSON.fromJson(json, ClearNotificationsRequestMessage.class);
                case LOGIN_SUCCESS, LOGIN_FAILURE -> GSON.fromJson(json, LoginResultMessage.class);
                case REGISTER_SUCCESS, REGISTER_FAILURE -> GSON.fromJson(json, RegisterResultMessage.class);
                case AUCTION_LIST_RESPONSE -> GSON.fromJson(json, AuctionListResponseMessage.class);
                case AUCTION_DETAILS_RESPONSE -> GSON.fromJson(json, AuctionDetailsResponseMessage.class);
                case NOTIFICATIONS_RESPONSE -> GSON.fromJson(json, NotificationsResponseMessage.class);
                case CREATE_ITEM_SUCCESS -> GSON.fromJson(json, CreateItemResultMessage.class);
                case BID_REQUEST -> GSON.fromJson(json, BidRequestMessage.class);
                case BID_ACCEPTED, BID_REJECTED -> GSON.fromJson(json, BidResultMessage.class);
                case PRICE_UPDATE -> GSON.fromJson(json, PriceUpdateMessage.class);
                case AUCTION_ENDED -> GSON.fromJson(json, AuctionEndedMessage.class);
                case NOTIFICATION -> GSON.fromJson(json, NotificationMessage.class);
                case ERROR -> GSON.fromJson(json, ErrorMessage.class);
                case PING -> GSON.fromJson(json, PingMessage.class);
                case PONG -> GSON.fromJson(json, PongMessage.class);
                default -> {
                    System.err.println("Unhandled message type: " + type);
                    yield null;
                }
            };
        } catch (Exception e) {
            System.err.println("Critical error while parsing JSON: " + json);
            e.printStackTrace();
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
