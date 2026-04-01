package space.blockway.waybucks.paper.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.shared.dto.LeaderboardEntryDto;
import space.blockway.waybucks.shared.dto.ShopDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open Waybucks GUIs and routes inventory events to them.
 *
 * <p>All GUI opens, clicks, and closes are funnelled through this manager so
 * the individual GUI classes stay free of event-listener boilerplate.</p>
 */
public class WaybucksGuiManager {

    private final WaybucksPaper plugin;

    /**
     * Maps each player's UUID to the WaybucksGui they currently have open.
     * Access from the main thread only (inventory events are synchronous).
     */
    private final Map<UUID, WaybucksGui> openGuis = new ConcurrentHashMap<>();

    public WaybucksGuiManager(WaybucksPaper plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // GUI openers
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link ShopGui} for {@code player}, registers it, and opens it.
     *
     * @param player the player to show the GUI to
     * @param shops  the shop list to display
     */
    public void openShopGui(Player player, List<ShopDto> shops) {
        ShopGui gui = new ShopGui(plugin, player, shops);
        openGuis.put(player.getUniqueId(), gui);
        gui.open(player);
    }

    /**
     * Creates a {@link LeaderboardGui} for {@code player}, registers it, and
     * opens it.
     *
     * @param player  the player to show the GUI to
     * @param entries the leaderboard entries to display
     */
    public void openLeaderboardGui(Player player, List<LeaderboardEntryDto> entries) {
        LeaderboardGui gui = new LeaderboardGui(plugin, player, entries);
        openGuis.put(player.getUniqueId(), gui);
        gui.open(player);
    }

    // -------------------------------------------------------------------------
    // Event routing
    // -------------------------------------------------------------------------

    /**
     * Called by {@link space.blockway.waybucks.paper.listeners.WaybucksInventoryListener}
     * when an {@link InventoryClickEvent} fires.
     *
     * <p>Cancels the event if the clicked inventory belongs to a tracked GUI,
     * then delegates the click to that GUI.</p>
     *
     * @param event the raw Bukkit inventory-click event
     */
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        WaybucksGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;

        // Only intercept clicks inside the tracked GUI's inventory
        if (!gui.getInventory().equals(event.getClickedInventory())) return;

        event.setCancelled(true);

        int slot      = event.getSlot();
        org.bukkit.inventory.ItemStack item = event.getCurrentItem();
        gui.handleClick(player, slot, item);
    }

    /**
     * Removes the tracked GUI for the given player UUID.
     * Called by the inventory-close listener.
     *
     * @param uuid the UUID of the player who closed their inventory
     */
    public void handleClose(UUID uuid) {
        openGuis.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns the currently open {@link WaybucksGui} for {@code uuid}, or
     * {@code null} if none is registered.
     *
     * @param uuid the player's UUID
     * @return the open GUI, or {@code null}
     */
    public WaybucksGui getOpenGui(UUID uuid) {
        return openGuis.get(uuid);
    }
}
