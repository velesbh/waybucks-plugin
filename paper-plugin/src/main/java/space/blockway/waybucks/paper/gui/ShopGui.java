package space.blockway.waybucks.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.shared.dto.ShopDto;

import java.util.ArrayList;
import java.util.List;

/**
 * A 6-row (54-slot) paginated shop GUI.
 *
 * <ul>
 *   <li>Rows 1-5 (slots 0-44): up to 45 shop items per page.</li>
 *   <li>Row 6 (slots 45-53): navigation bar – prev arrow (45), balance (49),
 *       next arrow (53); remaining slots filled with glass.</li>
 * </ul>
 */
public class ShopGui extends WaybucksGui {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_PREV      = 45;
    private static final int SLOT_BALANCE   = 49;
    private static final int SLOT_NEXT      = 53;

    private final Player player;
    private final List<ShopDto> shops;
    private int page = 0;

    /**
     * @param plugin the main plugin instance
     * @param player the player for whom this GUI is opened
     * @param shops  full (unfiltered) list of shops from the proxy cache
     */
    public ShopGui(WaybucksPaper plugin, Player player, List<ShopDto> shops) {
        super(plugin, plugin.getBwsConfig().getGuiTitle("shop-title"), 6);
        this.player = player;
        this.shops  = shops;
        rebuild();
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    @Override
    public void handleClick(Player player, int slot, ItemStack item) {
        if (slot < 0 || slot > 53) return;

        if (slot == SLOT_PREV) {
            if (page > 0) {
                page--;
                rebuild();
            }
            return;
        }

        if (slot == SLOT_NEXT) {
            int maxPage = Math.max(0, (shops.size() - 1) / ITEMS_PER_PAGE);
            if (page < maxPage) {
                page++;
                rebuild();
            }
            return;
        }

        // Ignore nav-bar filler slots
        if (slot >= 45) return;

        // Map slot → shop index
        int shopIndex = page * ITEMS_PER_PAGE + slot;
        if (shopIndex < 0 || shopIndex >= shops.size()) return;

        ShopDto shop = shops.get(shopIndex);
        if (!shop.isEnabled()) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage(
                    "shop-disabled", "shop", shop.getName()));
            return;
        }

        // Relay purchase and close GUI
        plugin.getMessageSender().sendShopBuy(player, shop.getId());
        player.closeInventory();
    }

    // -------------------------------------------------------------------------
    // Inventory population
    // -------------------------------------------------------------------------

    /**
     * Clears the inventory and re-populates it for the current page.
     */
    public void rebuild() {
        inventory.clear();

        // ---- Shop items (rows 1-5) ------------------------------------------
        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int shopIndex = startIndex + i;
            if (shopIndex >= shops.size()) break;
            inventory.setItem(i, buildShopItem(shops.get(shopIndex)));
        }

        // ---- Navigation bar (row 6) -----------------------------------------
        // Filler for entire row
        for (int s = 45; s <= 53; s++) {
            inventory.setItem(s, makeFiller());
        }

        // Previous page
        inventory.setItem(SLOT_PREV, buildNavArrow(false));

        // Balance display
        long balance = plugin.getBalanceCache().getOrDefault(player.getUniqueId(), 0L);
        inventory.setItem(SLOT_BALANCE, makeItem(
                Material.GOLD_INGOT,
                Component.text("Your Balance: W" + balance, NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of()
        ));

        // Next page
        inventory.setItem(SLOT_NEXT, buildNavArrow(true));
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private ItemStack buildShopItem(ShopDto shop) {
        // Resolve material
        Material mat = Material.GOLD_INGOT;
        if (shop.getIconMaterial() != null && !shop.getIconMaterial().isBlank()) {
            try {
                mat = Material.valueOf(shop.getIconMaterial().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                mat = Material.GOLD_INGOT;
            }
        }

        boolean enabled = shop.isEnabled();
        boolean noStock = shop.getStock() == 0;

        // Display name
        Component name = Component.text(shop.getName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
        if (!enabled || noStock) {
            name = Component.text(shop.getName(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false);
        }

        // Lore
        List<Component> lore = new ArrayList<>();
        if (shop.getDescription() != null && !shop.getDescription().isBlank()) {
            lore.add(Component.text(shop.getDescription(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Price: W" + shop.getPrice(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        int cmdCount = shop.getCommands() != null ? shop.getCommands().size() : 0;
        lore.add(Component.text("Commands: " + cmdCount, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (noStock) {
            lore.add(Component.text("OUT OF STOCK", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (enabled) {
            lore.add(Component.text("Click to purchase", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Unavailable", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        if (!enabled || noStock) {
            // Use a grey material to visually signal unavailability
            mat = Material.GRAY_STAINED_GLASS_PANE;
        }

        return makeItem(mat, name, lore);
    }

    /**
     * Builds an arrow navigation item.
     *
     * @param next {@code true} = next-page arrow, {@code false} = prev-page arrow
     */
    private ItemStack buildNavArrow(boolean next) {
        boolean canNavigate = next
                ? page < (Math.max(0, (shops.size() - 1) / ITEMS_PER_PAGE))
                : page > 0;

        Material mat = canNavigate ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE;
        String label = next ? ">> Next Page" : "<< Previous Page";
        NamedTextColor color = canNavigate ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY;

        return makeItem(mat,
                Component.text(label, color).decoration(TextDecoration.ITALIC, false),
                List.of());
    }
}
