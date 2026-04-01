package space.blockway.waybucks.paper.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.shared.WaybucksMessage;
import space.blockway.waybucks.shared.WaybucksMessageType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Sends {@link WaybucksMessage} payloads to the Velocity proxy over the plugin
 * messaging channel {@value #CHANNEL}.
 *
 * <p>All outgoing bytes are: the JSON-serialised {@link WaybucksMessage} encoded
 * as UTF-8, wrapped in a {@link DataOutputStream} as a UTF string.</p>
 */
public class WaybucksProxyMessageSender {

    /** Plugin-messaging channel shared with the Velocity plugin. */
    public static final String CHANNEL = "waybucks:events";

    private final WaybucksPaper plugin;
    private final Gson gson = new Gson();

    public WaybucksProxyMessageSender(WaybucksPaper plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Core send
    // -------------------------------------------------------------------------

    /**
     * Serialises {@code message} to JSON and sends it through {@code player}'s
     * outgoing plugin-messaging channel.
     *
     * @param player  the carrier player (must be online)
     * @param message the message to forward to Velocity
     */
    public void send(Player player, WaybucksMessage message) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Cannot send plugin message: carrier player is null or offline.");
            return;
        }

        try {
            String json = gson.toJson(message);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(json);
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to send plugin message to proxy", e);
        }
    }

    // -------------------------------------------------------------------------
    // Convenience senders
    // -------------------------------------------------------------------------

    /**
     * Requests the current balance for {@code player} from the proxy.
     */
    public void sendBalanceRequest(Player player) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", player.getUniqueId().toString());
        send(player, new WaybucksMessage(WaybucksMessageType.BALANCE_REQUEST, payload.toString()));
    }

    /**
     * Relays a transfer from {@code sender} to a target identified by username.
     * Velocity resolves the target UUID from the username.
     */
    public void sendTransfer(Player sender, String targetName, long amount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("fromUuid", sender.getUniqueId().toString());
        payload.addProperty("fromUsername", sender.getName());
        payload.addProperty("toUuid", "");
        payload.addProperty("toUsername", targetName);
        payload.addProperty("amount", amount);
        send(sender, new WaybucksMessage(WaybucksMessageType.TRANSFER_RELAY, payload.toString()));
    }

    /**
     * Relays a shop purchase request to Velocity.
     */
    public void sendShopBuy(Player buyer, String shopId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("buyerUuid", buyer.getUniqueId().toString());
        payload.addProperty("buyerUsername", buyer.getName());
        payload.addProperty("shopId", shopId);
        send(buyer, new WaybucksMessage(WaybucksMessageType.SHOP_BUY_RELAY, payload.toString()));
    }

    /**
     * Relays a daily-claim request for {@code player}.
     */
    public void sendDailyClaim(Player player) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", player.getUniqueId().toString());
        payload.addProperty("username", player.getName());
        send(player, new WaybucksMessage(WaybucksMessageType.DAILY_CLAIM_RELAY, payload.toString()));
    }

    /**
     * Relays an item-conversion request (deduct balance → give physical item).
     */
    public void sendItemConvert(Player player, long amount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", player.getUniqueId().toString());
        payload.addProperty("username", player.getName());
        payload.addProperty("amount", amount);
        send(player, new WaybucksMessage(WaybucksMessageType.ITEM_CONVERT_RELAY, payload.toString()));
    }

    /**
     * Relays an admin set-balance command for a target UUID.
     */
    public void sendAdminSet(Player admin, String targetUuid, long amount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", targetUuid);
        payload.addProperty("username", "");
        payload.addProperty("amount", amount);
        payload.addProperty("adminName", admin.getName());
        send(admin, new WaybucksMessage(WaybucksMessageType.ADMIN_SET_RELAY, payload.toString()));
    }

    /**
     * Relays an admin add-balance command for a target UUID.
     */
    public void sendAdminAdd(Player admin, String targetUuid, long amount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", targetUuid);
        payload.addProperty("username", "");
        payload.addProperty("amount", amount);
        payload.addProperty("adminName", admin.getName());
        send(admin, new WaybucksMessage(WaybucksMessageType.ADMIN_ADD_RELAY, payload.toString()));
    }

    /**
     * Relays an admin take-balance command for a target UUID.
     */
    public void sendAdminTake(Player admin, String targetUuid, long amount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", targetUuid);
        payload.addProperty("username", "");
        payload.addProperty("amount", amount);
        payload.addProperty("adminName", admin.getName());
        send(admin, new WaybucksMessage(WaybucksMessageType.ADMIN_TAKE_RELAY, payload.toString()));
    }

    /**
     * Requests the top-{@code limit} leaderboard from the proxy using
     * {@code player} as the carrier.
     *
     * @param player the online player to carry the message
     * @param limit  maximum number of entries to return
     */
    public void sendLeaderboardRequest(Player player, int limit) {
        JsonObject payload = new JsonObject();
        payload.addProperty("requestType", "LEADERBOARD");
        payload.addProperty("limit", limit);
        send(player, new WaybucksMessage(WaybucksMessageType.LEADERBOARD_REQUEST, payload.toString()));
    }

    /**
     * Sends a shop-list request to Velocity, re-using the LEADERBOARD_REQUEST
     * message type with {@code requestType = "SHOPS"}.
     * Velocity responds with a LEADERBOARD_RESPONSE containing a {@code shops}
     * JSON array, which {@code WaybucksPaperMessageHandler} routes to the GUI.
     */
    public void sendShopListRequest(Player player) {
        JsonObject payload = new JsonObject();
        payload.addProperty("requestType", "SHOPS");
        payload.addProperty("limit", 100);
        send(player, new WaybucksMessage(WaybucksMessageType.LEADERBOARD_REQUEST, payload.toString()));
    }

    /**
     * Notifies the proxy that a player has joined this server.
     * The proxy uses this to cache the player's server name.
     */
    public void sendPlayerJoin(Player player) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", player.getUniqueId().toString());
        payload.addProperty("username", player.getName());
        payload.addProperty("serverName", plugin.getBwsConfig().getServerName());
        send(player, new WaybucksMessage(WaybucksMessageType.PLAYER_JOIN, payload.toString()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns any one online player to use as a message carrier when the specific
     * target player is not available.
     */
    private Optional<Player> anyOnlinePlayer() {
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        return online.stream().map(p -> (Player) p).findFirst();
    }
}
