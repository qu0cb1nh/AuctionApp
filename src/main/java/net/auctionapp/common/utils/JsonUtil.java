package net.auctionapp.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mysql.cj.util.StringUtils;
import net.auctionapp.common.messages.*;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.LoginRequestMessage;
import net.auctionapp.common.messages.types.LoginResultMessage;
import net.auctionapp.common.messages.types.PriceUpdateMessage;
import net.auctionapp.common.messages.types.RegisterRequestMessage;
import net.auctionapp.common.messages.types.RegisterResultMessage;

public final class JsonUtil {

    private JsonUtil() { }

    private static final Gson gson = new Gson();

    public static String toJson(Object object) {
        return gson.toJson(object);
    }

    public static Message fromJson(String json) {
        try {
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

            if (jsonObject == null || !jsonObject.has("type")) {
                System.err.println("Invalid JSON or missing 'type' field: " + json);
                return null;
            }

            String typeString = jsonObject.get("type").getAsString();
            MessageType type = MessageType.valueOf(typeString);

            switch (type) {
                case LOGIN_REQUEST:
                    return gson.fromJson(json, LoginRequestMessage.class);
                case REGISTER_REQUEST:
                    return gson.fromJson(json, RegisterRequestMessage.class);
                case LOGIN_SUCCESS:
                case LOGIN_FAILURE:
                    return gson.fromJson(json, LoginResultMessage.class);
                case REGISTER_SUCCESS:
                case REGISTER_FAILURE:
                    return gson.fromJson(json, RegisterResultMessage.class);
                case BID_REQUEST:
                    return gson.fromJson(json, BidRequestMessage.class);
                case PRICE_UPDATE:
                    return gson.fromJson(json, PriceUpdateMessage.class);
                case ERROR:
                    return gson.fromJson(json, ErrorMessage.class);
                // Other cases will be added here as you develop new features
                default:
                    System.err.println("Unhandled message type: " + type);
                    // Return null so the calling thread can safely ignore this message
                    return null;
            }
        } catch (Exception e) {
            System.err.println("Critical error while parsing JSON: " + json);
            e.printStackTrace();
            return null;
        }
    }
}
