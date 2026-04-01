package space.blockway.waybucks.paper.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import space.blockway.waybucks.paper.gui.WaybucksGuiManager;

/**
 * Bukkit listener that forwards inventory events to {@link WaybucksGuiManager}.
 */
public class WaybucksInventoryListener implements Listener {

    private final WaybucksGuiManager guiManager;

    /**
     * @param guiManager the GUI manager that owns the open-GUI registry
     */
    public WaybucksInventoryListener(WaybucksGuiManager guiManager) {
        this.guiManager = guiManager;
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * Forwards all inventory-click events to the GUI manager.
     * The manager cancels the event and delegates to the specific GUI if the
     * clicked inventory belongs to a tracked Waybucks GUI.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        guiManager.handleClick(event);
    }

    /**
     * Removes the player's GUI registration when they close their inventory.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            guiManager.handleClose(player.getUniqueId());
        }
    }
}
