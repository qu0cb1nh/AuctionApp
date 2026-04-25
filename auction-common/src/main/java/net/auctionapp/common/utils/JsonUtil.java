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

            switch (type) {
                case LOGIN_REQUEST:
                    return GSON.fromJson(json, LoginRequestMessage.class);
                case REGISTER_REQUEST:
                    return GSON.fromJson(json, RegisterRequestMessage.class);
                case GET_AUCTION_LIST_REQUEST:
                    return GSON.fromJson(json, GetAuctionListRequestMessage.class);
                case GET_AUCTION_DETAILS_REQUEST:
                    return GSON.fromJson(json, GetAuctionDetailsRequestMessage.class);
                case CREATE_ITEM_REQUEST:
                    return GSON.fromJson(json, CreateItemRequestMessage.class);
                case LOGIN_SUCCESS:
                case LOGIN_FAILURE:
                    return GSON.fromJson(json, LoginResultMessage.class);
                case REGISTER_SUCCESS:
                case REGISTER_FAILURE:
                    return GSON.fromJson(json, RegisterResultMessage.class);
                case AUCTION_LIST_RESPONSE:
                    return GSON.fromJson(json, AuctionListResponseMessage.class);
                case AUCTION_DETAILS_RESPONSE:
                    return GSON.fromJson(json, AuctionDetailsResponseMessage.class);
                case CREATE_ITEM_SUCCESS:
                    return GSON.fromJson(json, CreateItemResultMessage.class);
                case BID_REQUEST:
                    return GSON.fromJson(json, BidRequestMessage.class);
                case BID_ACCEPTED:
                case BID_REJECTED:
                    return GSON.fromJson(json, BidResultMessage.class);
                case PRICE_UPDATE:
                    return GSON.fromJson(json, PriceUpdateMessage.class);
                case AUCTION_ENDED:
                    return GSON.fromJson(json, AuctionEndedMessage.class);
                case ERROR:
                    return GSON.fromJson(json, ErrorMessage.class);
                case PING:
                    return GSON.fromJson(json, PingMessage.class);
                case PONG:
                    return GSON.fromJson(json, PongMessage.class);
                default:
                    System.err.println("Unhandled message type: " + type);
                    return null;
            }
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
