package space.blockway.waybucks.velocity.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import space.blockway.waybucks.shared.WaybucksMessage;
import space.blockway.waybucks.shared.WaybucksMessageType;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;
import space.blockway.waybucks.velocity.managers.BalanceManager;
import space.blockway.waybucks.velocity.managers.DailyManager;
import space.blockway.waybucks.velocity.managers.ShopManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class WaybucksMessageHandler {

    private final BalanceManager balanceManager;
    private final ShopManager shopManager;
    private final DailyManager dailyManager;
    private final WaybucksMessageSender messageSender;
    private final WaybucksVelocityConfig config;
    private final Logger logger;
    private final Gson gson;

    public WaybucksMessageHandler(BalanceManager balanceManager,
                                  ShopManager shopManager,
                                  DailyManager dailyManager,
                                  WaybucksMessageSender messageSender,
                                  WaybucksVelocityConfig config,
                                  Logger logger) {
        this.balanceManager = balanceManager;
        this.shopManager = shopManager;
        this.dailyManager = dailyManager;
        this.messageSender = messageSender;
        this.config = config;
        this.logger = logger;
        this.gson = new Gson();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Only process messages originating from a backend server
        if (!(event.getSource() instanceof ServerConnection serverConnection)) {
            return;
        }

        // Only handle our channel
        if (!event.getIdentifier().equals(WaybucksMessageSender.CHANNEL)) {
            return;
        }

        // Mark as handled so it doesn't get forwarded to the client
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        String raw = new String(event.getData(), StandardCharsets.UTF_8);
        WaybucksMessage message;
        try {
            message = gson.fromJson(raw, WaybucksMessage.class);
        } catch (Exception ex) {
            logger.error("[WaybucksMessageHandler] Failed to parse plugin message: {}", raw, ex);
            return;
        }

        try {
            dispatch(message);
        } catch (Exception ex) {
            logger.error("[WaybucksMessageHandler] Error dispatching message type {}: {}",
                    message.getType(), ex.getMessage(), ex);
        }
    }

    private void dispatch(WaybucksMessage message) {
        WaybucksMessageType type = message.getType();
        String payload = message.getPayload();

        switch (type) {
            case PLAYER_JOIN -> handlePlayerJoin(payload);
            case BALANCE_REQUEST -> handleBalanceRequest(payload);
            case TRANSFER_RELAY -> handleTransferRelay(payload);
            case SHOP_BUY_RELAY -> handleShopBuyRelay(payload);
            case DAILY_CLAIM_RELAY -> handleDailyClaimRelay(payload);
            case ITEM_CONVERT_RELAY -> handleItemConvertRelay(payload);
            case ADMIN_SET_RELAY -> handleAdminSetRelay(payload);
            case ADMIN_ADD_RELAY -> handleAdminAddRelay(payload);
            case ADMIN_TAKE_RELAY -> handleAdminTakeRelay(payload);
            case LEADERBOARD_REQUEST -> handleLeaderboardRequest(payload);
            default -> logger.warn("[WaybucksMessageHandler] Unhandled message type: {}", type);
        }
    }

    // -------------------------------------------------------------------------
    // PLAYER_JOIN
    // -------------------------------------------------------------------------
    private void handlePlayerJoin(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
        String username = obj.get("username").getAsString();
        balanceManager.ensurePlayer(uuid, username);
    }

    // -------------------------------------------------------------------------
    // BALANCE_REQUEST
    // -------------------------------------------------------------------------
    private void handleBalanceRequest(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());

        long balance = balanceManager.getBalance(uuid);
        String symbol = config.getCurrencySymbol();

        JsonObject resp = new JsonObject();
        resp.addProperty("uuid", uuid.toString());
        resp.addProperty("balance", balance);
        resp.addProperty("symbol", symbol);

        messageSender.sendToPlayer(uuid, WaybucksMessageType.BALANCE_RESPONSE, resp.toString());
    }

    // -------------------------------------------------------------------------
    // TRANSFER_RELAY
    // -------------------------------------------------------------------------
    private void handleTransferRelay(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID fromUuid = UUID.fromString(obj.get("fromUuid").getAsString());
        String fromUsername = obj.get("fromUsername").getAsString();
        UUID toUuid = UUID.fromString(obj.get("toUuid").getAsString());
        String toUsername = obj.get("toUsername").getAsString();
        long amount = obj.get("amount").getAsLong();

        BalanceManager.BalanceResult result = balanceManager.transfer(fromUuid, fromUsername, toUuid, toUsername, amount);

        JsonObject resp = new JsonObject();
        resp.addProperty("result", result.name());

        messageSender.sendToPlayer(fromUuid, WaybucksMessageType.TRANSFER_RESULT, resp.toString());
    }

    // -------------------------------------------------------------------------
    // SHOP_BUY_RELAY
    // -------------------------------------------------------------------------
    private void handleShopBuyRelay(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID buyerUuid = UUID.fromString(obj.get("buyerUuid").getAsString());
        String buyerUsername = obj.get("buyerUsername").getAsString();
        String shopId = obj.get("shopId").getAsString();

        // Purchase returns a result; we need the shop details for the response too
        var shop = shopManager.getShop(shopId);
        ShopManager.ShopResult result = shopManager.purchase(buyerUuid, buyerUsername, shopId);

        JsonObject resp = new JsonObject();
        resp.addProperty("result", result.name());
        if (shop != null) {
            resp.addProperty("shopName", shop.getName());
            resp.addProperty("price", shop.getPrice());
        } else {
            resp.addProperty("shopName", shopId);
            resp.addProperty("price", 0L);
        }

        messageSender.sendToPlayer(buyerUuid, WaybucksMessageType.SHOP_BUY_RESULT, resp.toString());
    }

    // -------------------------------------------------------------------------
    // DAILY_CLAIM_RELAY
    // -------------------------------------------------------------------------
    private void handleDailyClaimRelay(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
        String username = obj.get("username").getAsString();

        DailyManager.DailyClaimResult claimResult = dailyManager.claimDaily(uuid, username);

        JsonObject resp = new JsonObject();
        resp.addProperty("result", claimResult.result().name());
        resp.addProperty("amount", claimResult.amount());
        resp.addProperty("streak", claimResult.streak());
        resp.addProperty("nextClaimAt", claimResult.nextClaimAt());

        messageSender.sendToPlayer(uuid, WaybucksMessageType.DAILY_CLAIM_RESULT, resp.toString());
    }

    // -------------------------------------------------------------------------
    // ITEM_CONVERT_RELAY
    // -------------------------------------------------------------------------
    private void handleItemConvertRelay(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
        String username = obj.get("username").getAsString();
        long amount = obj.get("amount").getAsLong();

        BalanceManager.BalanceResult takeResult = balanceManager.takeBalance(uuid, amount,
                "Item convert by " + username);

        JsonObject resp = new JsonObject();
        if (takeResult == BalanceManager.BalanceResult.SUCCESS) {
            resp.addProperty("result", "SUCCESS");
            resp.addProperty("amount", amount);
        } else {
            resp.addProperty("result", "INSUFFICIENT");
            resp.addProperty("amount", amount);
        }

        messageSender.sendToPlayer(uuid, WaybucksMessageType.ITEM_CONVERT_RESULT, resp.toString());
    }

    // -------------------------------------------------------------------------
    // ADMIN_SET_RELAY
    // -------------------------------------------------------------------------
    private void handleAdminSetRelay(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
        String username = obj.get("username").getAsString();
        long amount = obj.get("amount").getAsLong();
        String adminName = obj.get("adminName").getAsString();

        balanceManager.setBalance(uuid, amount, "Admin set by " + adminName);
    }

    // -------------------------------------------------------------------------
    // ADMIN_ADD_RELAY
    // -------------------------------------------------------------------------
    private void handleAdminAddRelay(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
        String username = obj.get("username").getAsString();
        long amount = obj.get("amount").getAsLong();
        String note = obj.get("note").getAsString();

        balanceManager.addBalance(uuid, amount, note);
    }

    // -------------------------------------------------------------------------
    // ADMIN_TAKE_RELAY
    // -------------------------------------------------------------------------
    private void handleAdminTakeRelay(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
        String username = obj.get("username").getAsString();
        long amount = obj.get("amount").getAsLong();
        String note = obj.get("note").getAsString();

        balanceManager.takeBalance(uuid, amount, note);
    }

    // -------------------------------------------------------------------------
    // LEADERBOARD_REQUEST
    // -------------------------------------------------------------------------
    private void handleLeaderboardRequest(String payload) {
        JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
        int limit = obj.has("limit") ? obj.get("limit").getAsInt() : 10;

        List<space.blockway.waybucks.shared.dto.LeaderboardEntryDto> entries = balanceManager.getLeaderboard(limit);

        JsonObject resp = new JsonObject();
        resp.add("entries", gson.toJsonTree(entries));

        // LEADERBOARD_RESPONSE has no specific player target — broadcast to a sentinel or log only.
        // The target UUID in the original message may contain the requester's UUID.
        // For robustness, we log the response; individual servers should request via REST for leaderboard data.
        // If the original message contained a target UUID we can respond to them.
        logger.debug("[WaybucksMessageHandler] Leaderboard response generated with {} entries", entries.size());
    }
}
