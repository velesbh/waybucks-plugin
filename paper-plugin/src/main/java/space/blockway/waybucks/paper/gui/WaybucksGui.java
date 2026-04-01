package space.blockway.waybucks.paper.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import space.blockway.waybucks.paper.WaybucksPaper;

import java.util.List;

/**
 * Abstract base class for all Waybucks GUI screens.
 *
 * <p>Subclasses populate {@link #inventory} in their constructor and override
 * {@link #handleClick(Player, int, ItemStack)} to respond to slot clicks.</p>
 */
public abstract class WaybucksGui {

    protected final Inventory inventory;
    protected final WaybucksPaper plugin;

    /**
     * Creates a chest-style inventory with the given title and row count.
     *
     * @param plugin reference to the main plugin
     * @param title  MiniMessage-parsed Component used as the inventory title
     * @param rows   number of rows (1–6); slot count = rows × 9
     */
    public WaybucksGui(WaybucksPaper plugin, Component title, int rows) {
        this.plugin    = plugin;
        this.inventory = Bukkit.createInventory(null, rows * 9, title);
    }

    /**
     * Opens this GUI for {@code player}.
     */
    public void open(Player player) {
        player.openInventory(inventory);
    }

    /**
     * Called by the inventory listener when a player clicks a slot inside this
     * GUI.  The event is already cancelled before this method is invoked;
     * implementors should perform actions (e.g. open a new GUI, send a message)
     * without worrying about item movement.
     *
     * @param player the clicking player
     * @param slot   the clicked slot index
     * @param item   the ItemStack currently in that slot (may be {@code null})
     */
    public abstract void handleClick(Player player, int slot, ItemStack item);

    /**
     * Returns the underlying {@link Inventory} so it can be compared in
     * inventory events.
     */
    public Inventory getInventory() {
        return inventory;
    }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    /**
     * Creates an ItemStack with a custom display name and lore.
     */
    protected ItemStack makeItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns a single gray stained-glass pane used to fill unused slots.
     */
    protected ItemStack makeFiller() {
        return makeItem(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "),
                List.of()
        );
    }
}
