package space.blockway.waybucks.paper.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;

/**
 * Handles player-related events: notifying the proxy on join and processing
 * Waybucks-note item redemptions on right-click.
 */
public class WaybucksPlayerListener implements Listener {

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    /**
     * @param plugin the main plugin instance
     * @param sender the message sender used to relay events to the proxy
     */
    public WaybucksPlayerListener(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * Announces the player's join to the proxy and schedules a balance request
     * with a 5-tick delay (giving the proxy time to load the player's data).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Announce join to proxy (updates server-name in proxy's player-map)
        sender.sendPlayerJoin(player);

        // Request balance after a short delay so the proxy has time to register
        // the player before responding.
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> {
                    if (player.isOnline()) {
                        sender.sendBalanceRequest(player);
                    }
                }, 5L);
    }

    /**
     * Handles right-clicking with a Waybucks physical note item:
     * <ol>
     *   <li>Checks that the item in hand carries the {@code waybucks:amount} PDC tag.</li>
     *   <li>Consumes one item from the player's hand.</li>
     *   <li>Relays an ADMIN_ADD_RELAY to the proxy to credit the balance.</li>
     *   <li>Sends the player a confirmation message.</li>
     * </ol>
     *
     * <p>Only the main-hand RIGHT_CLICK action is processed to prevent double
     * triggers from off-hand.</p>
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        // Only process right-click actions
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Ignore off-hand events (Paper fires the event twice)
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey amountKey = plugin.getWaybucksAmountKey();

        if (!pdc.has(amountKey, PersistentDataType.LONG)) return;

        // Cancel the underlying block/entity interaction
        event.setCancelled(true);

        long amount = pdc.get(amountKey, PersistentDataType.LONG);
        Player player = event.getPlayer();

        // Remove one note from the player's hand
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Credit the player's balance via the proxy using admin-add (item redeem)
        sender.sendAdminAdd(player, player.getUniqueId().toString(), amount);

        // Confirmation message
        Component msg = plugin.getBwsConfig().getPrefixedMessage(
                "item-redeemed",
                "amount", String.valueOf(amount)
        );
        player.sendMessage(msg);
    }
}
