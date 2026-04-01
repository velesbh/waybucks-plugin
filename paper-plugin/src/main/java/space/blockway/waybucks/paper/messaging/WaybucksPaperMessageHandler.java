package space.blockway.waybucks.paper.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.shared.WaybucksMessage;
import space.blockway.waybucks.shared.WaybucksMessageType;
import space.blockway.waybucks.shared.dto.LeaderboardEntryDto;
import space.blockway.waybucks.shared.dto.ShopDto;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Receives inbound plugin-channel messages from the Velocity proxy and dispatches
 * them by {@link WaybucksMessageType}.
 */
public class WaybucksPaperMessageHandler implements PluginMessageListener {

    private final WaybucksPaper plugin;
    private final Gson gson = new Gson();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public WaybucksPaperMessageHandler(WaybucksPaper plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // PluginMessageListener
    // -------------------------------------------------------------------------

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!WaybucksProxyMessageSender.CHANNEL.equals(channel)) {
            return;
        }

        String json;
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(message));
            json = dis.readUTF();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read inbound plugin message", e);
            return;
        }

        WaybucksMessage waybucksMessage;
        try {
            waybucksMessage = gson.fromJson(json, WaybucksMessage.class);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse WaybucksMessage JSON: " + json, e);
            return;
        }

        if (waybucksMessage == null || waybucksMessage.getType() == null) {
            plugin.getLogger().warning("Received null or typeless WaybucksMessage.");
            return;
        }

        dispatch(waybucksMessage);
    }

    // -------------------------------------------------------------------------
    // Dispatcher
    // -------------------------------------------------------------------------

    private void dispatch(WaybucksMessage message) {
        switch (message.getType()) {
            case BALANCE_RESPONSE -> handleBalanceResponse(message, true);
            case BALANCE_UPDATED  -> handleBalanceResponse(message, false);
            case TRANSFER_RESULT  -> handleTransferResult(message);
            case SHOP_BUY_RESULT  -> handleShopBuyResult(message);
            case DAILY_CLAIM_RESULT -> handleDailyClaimResult(message);
            case ITEM_CONVERT_RESULT -> handleItemConvertResult(message);
            case LEADERBOARD_RESPONSE -> handleLeaderboardResponse(message);
            case RESULT           -> handleGenericResult(message);
            default -> {
                // upstream-only types ignored
            }
        }
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    /**
     * Handles BALANCE_RESPONSE and BALANCE_UPDATED.
     * Updates the in-memory balance cache; if {@code notify} is {@code true}
     * (BALANCE_RESPONSE) the player also receives a chat message.
     */
    private void handleBalanceResponse(WaybucksMessage message, boolean notify) {
        JsonObject payload = parsePayload(message.getPayload());
        if (payload == null) return;

        String uuidStr = getStr(payload, "uuid");
        long balance   = getLong(payload, "balance");
        String symbol  = getStr(payload, "symbol", "W");

        if (uuidStr == null) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID in balance response: " + uuidStr);
            return;
        }

        plugin.getBalanceCache().put(uuid, balance);

        if (notify) {
            Player target = plugin.getServer().getPlayer(uuid);
            if (target != null && target.isOnline()) {
                Component msg = plugin.getBwsConfig().getPrefixedMessage(
                        "balance",
                        "symbol", symbol,
                        "amount", String.valueOf(balance)
                );
                target.sendMessage(msg);
            }
        }
    }

    /**
     * Handles TRANSFER_RESULT — informs the sender of success or failure.
     */
    private void handleTransferResult(WaybucksMessage message) {
        JsonObject payload = parsePayload(message.getPayload());
        if (payload == null) return;

        String result    = getStr(payload, "result", "ERROR");
        String targetUuidStr = message.getTargetUuid();
        if (targetUuidStr == null) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        Component msg;
        switch (result) {
            case "SUCCESS" -> msg = plugin.getBwsConfig().getPrefixedMessage("transfer-sent");
            case "INSUFFICIENT_FUNDS" -> msg = plugin.getBwsConfig().getPrefixedMessage("insufficient-funds");
            case "PLAYER_NOT_FOUND"   -> msg = plugin.getBwsConfig().getPrefixedMessage("player-not-found");
            default -> msg = plugin.getBwsConfig().getPrefixedMessage("error-generic");
        }
        player.sendMessage(msg);
    }

    /**
     * Handles SHOP_BUY_RESULT.
     */
    private void handleShopBuyResult(WaybucksMessage message) {
        JsonObject payload = parsePayload(message.getPayload());
        if (payload == null) return;

        String result    = getStr(payload, "result", "ERROR");
        String shopName  = getStr(payload, "shopName", "Unknown");
        long   price     = getLong(payload, "price");
        String targetUuidStr = message.getTargetUuid();
        if (targetUuidStr == null) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        Component msg;
        switch (result) {
            case "SUCCESS" -> {
                msg = plugin.getBwsConfig().getPrefixedMessage(
                        "shop-purchased",
                        "shop", shopName,
                        "price", String.valueOf(price)
                );
                // Refresh balance after purchase
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> plugin.getMessageSender().sendBalanceRequest(player), 10L);
            }
            case "INSUFFICIENT_FUNDS" -> msg = plugin.getBwsConfig().getPrefixedMessage("insufficient-funds");
            case "OUT_OF_STOCK"       -> msg = plugin.getBwsConfig().getPrefixedMessage("shop-out-of-stock", "shop", shopName);
            case "SHOP_DISABLED"      -> msg = plugin.getBwsConfig().getPrefixedMessage("shop-disabled", "shop", shopName);
            case "MAX_PURCHASES"      -> msg = plugin.getBwsConfig().getPrefixedMessage("shop-max-purchases", "shop", shopName);
            default -> msg = plugin.getBwsConfig().getPrefixedMessage("error-generic");
        }
        player.sendMessage(msg);
    }

    /**
     * Handles DAILY_CLAIM_RESULT.
     */
    private void handleDailyClaimResult(WaybucksMessage message) {
        JsonObject payload = parsePayload(message.getPayload());
        if (payload == null) return;

        String result      = getStr(payload, "result", "ERROR");
        long   amount      = getLong(payload, "amount");
        long   streak      = getLong(payload, "streak");
        long   nextClaimAt = getLong(payload, "nextClaimAt");
        String targetUuidStr = message.getTargetUuid();
        if (targetUuidStr == null) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        Component msg;
        if ("SUCCESS".equals(result)) {
            msg = plugin.getBwsConfig().getPrefixedMessage(
                    "daily-claimed",
                    "amount", String.valueOf(amount),
                    "streak", String.valueOf(streak)
            );
        } else if ("ALREADY_CLAIMED".equals(result)) {
            String timeRemaining = formatTimeRemaining(nextClaimAt);
            msg = plugin.getBwsConfig().getPrefixedMessage(
                    "daily-already-claimed",
                    "time", timeRemaining
            );
        } else {
            msg = plugin.getBwsConfig().getPrefixedMessage("error-generic");
        }
        player.sendMessage(msg);
    }

    /**
     * Handles ITEM_CONVERT_RESULT — on success gives the physical Waybucks item.
     */
    private void handleItemConvertResult(WaybucksMessage message) {
        JsonObject payload = parsePayload(message.getPayload());
        if (payload == null) return;

        String result = getStr(payload, "result", "ERROR");
        long   amount = getLong(payload, "amount");
        String targetUuidStr = message.getTargetUuid();
        if (targetUuidStr == null) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        if ("SUCCESS".equals(result)) {
            // Give item on main thread
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> giveWaybucksItem(player, amount));
        } else if ("INSUFFICIENT_FUNDS".equals(result)) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("insufficient-funds"));
        } else {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("error-generic"));
        }
    }

    /**
     * Handles LEADERBOARD_RESPONSE.
     * Checks whether the payload contains a {@code shops} array (shop-list
     * response) or an {@code entries} array (leaderboard response) and routes
     * accordingly.
     */
    private void handleLeaderboardResponse(WaybucksMessage message) {
        JsonObject payload = parsePayload(message.getPayload());
        if (payload == null) return;

        String targetUuidStr = message.getTargetUuid();
        Player player = null;
        if (targetUuidStr != null) {
            try {
                player = plugin.getServer().getPlayer(UUID.fromString(targetUuidStr));
            } catch (IllegalArgumentException ignored) {}
        }

        // Shop-list response
        if (payload.has("shops")) {
            JsonArray shopsArray = payload.getAsJsonArray("shops");
            List<ShopDto> shops = new ArrayList<>();
            for (JsonElement el : shopsArray) {
                try {
                    shops.add(gson.fromJson(el, ShopDto.class));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse ShopDto: " + e.getMessage());
                }
            }
            plugin.getShopCache().clear();
            plugin.getShopCache().addAll(shops);

            if (player != null && player.isOnline()) {
                final Player finalPlayer = player;
                final List<ShopDto> finalShops = new ArrayList<>(shops);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.getGuiManager().openShopGui(finalPlayer, finalShops));
            }
            return;
        }

        // Leaderboard response
        if (payload.has("entries")) {
            JsonArray entriesArray = payload.getAsJsonArray("entries");
            List<LeaderboardEntryDto> entries = new ArrayList<>();
            for (JsonElement el : entriesArray) {
                try {
                    entries.add(gson.fromJson(el, LeaderboardEntryDto.class));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse LeaderboardEntryDto: " + e.getMessage());
                }
            }

            if (player != null && player.isOnline()) {
                final Player finalPlayer = player;
                final List<LeaderboardEntryDto> finalEntries = new ArrayList<>(entries);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.getGuiManager().openLeaderboardGui(finalPlayer, finalEntries));
            }
        }
    }

    /**
     * Handles a generic RESULT message — shown as info or error depending on
     * the result field.
     */
    private void handleGenericResult(WaybucksMessage message) {
        JsonObject payload = parsePayload(message.getPayload());
        if (payload == null) return;

        String result  = getStr(payload, "result", "ERROR");
        String rawText = getStr(payload, "message", "");
        String targetUuidStr = message.getTargetUuid();
        if (targetUuidStr == null) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        Component body = rawText.isEmpty()
                ? Component.empty()
                : miniMessage.deserialize(rawText);

        if ("SUCCESS".equals(result) || "INFO".equals(result)) {
            Component prefix = plugin.getBwsConfig().getPrefixedMessage("info-generic");
            player.sendMessage(prefix.append(body));
        } else {
            Component prefix = plugin.getBwsConfig().getPrefixedMessage("error-generic");
            player.sendMessage(prefix.append(body));
        }
    }

    // -------------------------------------------------------------------------
    // Item giving
    // -------------------------------------------------------------------------

    /**
     * Creates a Waybucks physical note item and gives it to {@code player}.
     * Material, custom model data, display name, and lore are sourced from the
     * plugin config.  The item carries a PersistentData long tag so it can be
     * redeemed later.
     *
     * <p>This method MUST be called on the main server thread.</p>
     */
    public void giveWaybucksItem(Player player, long amount) {
        String materialName = plugin.getConfig().getString("item.material", "PAPER");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();

        // Custom model data (optional)
        int customModelData = plugin.getConfig().getInt("item.custom-model-data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        // Display name
        String nameTemplate = plugin.getConfig().getString(
                "item.display-name", "<gold><bold>Waybucks Note</bold></gold> <yellow>(W{amount})</yellow>");
        String parsedName = nameTemplate.replace("{amount}", String.valueOf(amount));
        meta.displayName(miniMessage.deserialize(parsedName));

        // Lore
        String loreTemplate = plugin.getConfig().getString(
                "item.lore", "<gray>Value: <gold>W{amount}</gold></gray>\n<dark_gray>Right-click to redeem.</dark_gray>");
        List<Component> lore = new ArrayList<>();
        for (String line : loreTemplate.split("\n")) {
            lore.add(miniMessage.deserialize(line.replace("{amount}", String.valueOf(amount))));
        }
        meta.lore(lore);

        // PersistentData
        meta.getPersistentDataContainer().set(
                plugin.getWaybucksAmountKey(),
                PersistentDataType.LONG,
                amount
        );

        item.setItemMeta(meta);

        // Give item; drop at feet if inventory is full
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendMessage(plugin.getBwsConfig().getPrefixedMessage(
                "item-received",
                "amount", String.valueOf(amount)
        ));
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private JsonObject parsePayload(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not parse payload as JSON: " + payload);
            return null;
        }
    }

    private String getStr(JsonObject obj, String key) {
        return getStr(obj, key, null);
    }

    private String getStr(JsonObject obj, String key, String def) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : def;
    }

    private long getLong(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsLong() : 0L;
    }

    /**
     * Formats the remaining time in seconds until {@code nextClaimAt} epoch ms
     * as a human-readable string such as "5h 30m".
     */
    private String formatTimeRemaining(long nextClaimAtMs) {
        long nowMs       = System.currentTimeMillis();
        long remainingMs = nextClaimAtMs - nowMs;
        if (remainingMs <= 0) return "now";

        long hours   = TimeUnit.MILLISECONDS.toHours(remainingMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }
}
