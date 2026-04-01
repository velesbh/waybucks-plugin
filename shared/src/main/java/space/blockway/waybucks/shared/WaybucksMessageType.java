package space.blockway.waybucks.shared;

/**
 * All upstream (Paper → Velocity) and downstream (Velocity → Paper) message types.
 */
public enum WaybucksMessageType {
    // Upstream — Paper sends these to Velocity
    PLAYER_JOIN,
    BALANCE_REQUEST,
    BALANCE_SET_RELAY,
    BALANCE_ADD_RELAY,
    BALANCE_TAKE_RELAY,
    TRANSFER_RELAY,
    SHOP_BUY_RELAY,
    DAILY_CLAIM_RELAY,
    ITEM_CONVERT_RELAY,       // player converts waybucks item → balance
    ADMIN_SET_RELAY,
    ADMIN_ADD_RELAY,
    ADMIN_TAKE_RELAY,
    LEADERBOARD_REQUEST,

    // Downstream — Velocity sends these to Paper
    BALANCE_RESPONSE,
    BALANCE_UPDATED,          // pushed after any balance change
    TRANSFER_RESULT,
    SHOP_BUY_RESULT,
    DAILY_CLAIM_RESULT,
    ITEM_CONVERT_RESULT,
    LEADERBOARD_RESPONSE,
    RESULT,                   // generic result with code + message
}
