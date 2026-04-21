package net.auctionapp.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.BidRequestMessage;
import net.auctionapp.common.messages.types.BidResultMessage;
import net.auctionapp.common.messages.types.AuctionDetailsResponseMessage;
import net.auctionapp.common.messages.types.AuctionEndedMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.CreateItemRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetAuctionDetailsRequestMessage;
import net.auctionapp.common.messages.types.GetAuctionListRequestMessage;
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
                case GET_AUCTION_LIST_REQUEST:
                    return gson.fromJson(json, GetAuctionListRequestMessage.class);
                case GET_AUCTION_DETAILS_REQUEST:
                    return gson.fromJson(json, GetAuctionDetailsRequestMessage.class);
                case CREATE_ITEM_REQUEST:
                    return gson.fromJson(json, CreateItemRequestMessage.class);
                case LOGIN_SUCCESS:
                case LOGIN_FAILURE:
                    return gson.fromJson(json, LoginResultMessage.class);
                case REGISTER_SUCCESS:
                case REGISTER_FAILURE:
                    return gson.fromJson(json, RegisterResultMessage.class);
                case AUCTION_LIST_RESPONSE:
                    return gson.fromJson(json, AuctionListResponseMessage.class);
                case AUCTION_DETAILS_RESPONSE:
                    return gson.fromJson(json, AuctionDetailsResponseMessage.class);
                case BID_REQUEST:
                    return gson.fromJson(json, BidRequestMessage.class);
                case BID_ACCEPTED:
                case BID_REJECTED:
                    return gson.fromJson(json, BidResultMessage.class);
                case PRICE_UPDATE:
                    return gson.fromJson(json, PriceUpdateMessage.class);
                case AUCTION_ENDED:
                    return gson.fromJson(json, AuctionEndedMessage.class);
                case ERROR:
                    return gson.fromJson(json, ErrorMessage.class);
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
}
