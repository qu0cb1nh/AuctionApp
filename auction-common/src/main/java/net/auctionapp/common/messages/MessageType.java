package net.auctionapp.common.messages;

/**
 * Enum defining the types of messages sent back and forth between the Client and Server.
 */
public enum MessageType {

    // === Client -> Server Messages ===

    /**
     * Request to log in with a username/password.
     */
    LOGIN_REQUEST,

    /**
     * Request to register a new account.
     */
    REGISTER_REQUEST,

    /**
     * Request for the list of auction sessions.
     */
    GET_AUCTION_LIST_REQUEST,

    /**
     * Request for detailed information about a specific auction session.
     */
    GET_AUCTION_DETAILS_REQUEST,

    /**
     * Request for the current user's notifications inbox.
     */
    GET_NOTIFICATIONS_REQUEST,

    /**
     * Sends a bid for an item.
     */
    BID_REQUEST,

    /**
     * (Seller) Request to create a new item for auction.
     */
    CREATE_ITEM_REQUEST,

    /**
     * Mark a specific notification as read.
     */
    MARK_NOTIFICATION_READ_REQUEST,

    /**
     * Clear one notification.
     */
    CLEAR_NOTIFICATIONS_REQUEST,


    // === Server -> Client Messages ===

    /**
     * Response for a successful login, can include user information.
     */
    LOGIN_SUCCESS,

    /**
     * Response for a failed login, includes the reason.
     */
    LOGIN_FAILURE,

    /**
     * Response for a successful registration.
     */
    REGISTER_SUCCESS,

    /**
     * Response for a failed registration, includes the reason (e.g., username already exists).
     */
    REGISTER_FAILURE,

    /**
     * Response containing the list of auction sessions.
     */
    AUCTION_LIST_RESPONSE,

    /**
     * Response containing detailed information about an auction session.
     */
    AUCTION_DETAILS_RESPONSE,

    /**
     * Response containing notifications for the authenticated user.
     */
    NOTIFICATIONS_RESPONSE,

    /**
     * Response after creating a new auction successfully.
     */
    CREATE_ITEM_SUCCESS,

    /**
     * Response to the bidding client that their bid was accepted.
     */
    BID_ACCEPTED,

    /**
     * Response to the bidding client that their bid was rejected (e.g., lower than the current price).
     */
    BID_REJECTED,

    /**
     * (Broadcast) Notifies all clients of the new price and the leading bidder.
     */
    PRICE_UPDATE,

    /**
     * (Broadcast) Announces that an auction has ended, includes the winner.
     */
    AUCTION_ENDED,

    /**
     * Sends a general error message to a specific client.
     */
    ERROR,

    /**
     * Sends a simple text-based notification message.
     */
    NOTIFICATION,

    /**
     * Sent by the Client to the Server at regular intervals (e.g., every 30 seconds).
     * The Server should reset its idle timeout counter and immediately
     * reply with a PONG message.
     */
    PING,

    /**
     * Sent by the Server to the Client in direct response to a PING message.
     * The Client can use this to verify the connection is healthy, no further reply is needed.
     */
    PONG,

}
