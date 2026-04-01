package space.blockway.waybucks.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.shared.dto.LeaderboardEntryDto;

import java.util.List;

/**
 * A 6-row (54-slot) leaderboard GUI.
 *
 * <p>Layout (slots are 0-indexed, left-to-right, top-to-bottom):</p>
 * <ul>
 *   <li>Rows 2-4 (slots 9-35): 3 rows × 7 centred columns (slots 10-16, 19-25,
 *       28-34) show up to 21 player-head entries.</li>
 *   <li>All other slots: gray glass filler, except slot 49 which is a BARRIER
 *       "Close" button.</li>
 * </ul>
 */
public class LeaderboardGui extends WaybucksGui {

    /** The 21 display slots arranged as three centred rows of 7. */
    private static final int[] DISPLAY_SLOTS = buildDisplaySlots();

    private static final int SLOT_CLOSE = 49;

    private final List<LeaderboardEntryDto> entries;

    /**
     * @param plugin  the main plugin instance
     * @param player  the viewer (unused after construction but kept for future use)
     * @param entries ordered list of leaderboard entries (rank 1 first)
     */
    public LeaderboardGui(WaybucksPaper plugin, Player player, List<LeaderboardEntryDto> entries) {
        super(plugin, plugin.getBwsConfig().getGuiTitle("leaderboard-title"), 6);
        this.entries = entries;
        populate();
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    @Override
    public void handleClick(Player player, int slot, ItemStack item) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
        // All other slots are informational; no action required.
    }

    // -------------------------------------------------------------------------
    // Population
    // -------------------------------------------------------------------------

    private void populate() {
        // Fill everything with glass first
        for (int s = 0; s < 54; s++) {
            inventory.setItem(s, makeFiller());
        }

        // Close button
        inventory.setItem(SLOT_CLOSE, makeItem(
                Material.BARRIER,
                Component.text("Close", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false),
                List.of()
        ));

        // Entry heads
        int limit = Math.min(entries.size(), DISPLAY_SLOTS.length);
        for (int i = 0; i < limit; i++) {
            LeaderboardEntryDto entry = entries.get(i);
            inventory.setItem(DISPLAY_SLOTS[i], buildEntryItem(entry));
        }
    }

    // -------------------------------------------------------------------------
    // Item builder
    // -------------------------------------------------------------------------

    private ItemStack buildEntryItem(LeaderboardEntryDto entry) {
        int rank = entry.getRank();

        // Choose a rank-appropriate name colour
        NamedTextColor rankColor;
        if (rank == 1)      rankColor = NamedTextColor.GOLD;
        else if (rank == 2) rankColor = NamedTextColor.GRAY;
        else if (rank == 3) rankColor = NamedTextColor.DARK_RED;
        else                rankColor = NamedTextColor.WHITE;

        Component name = Component.text(rank + ". " + entry.getUsername(), rankColor)
                .decoration(TextDecoration.BOLD, rank <= 3)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = List.of(
                Component.text("Balance: W" + entry.getBalance(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        );

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();

        // Set owner by username (non-deprecated offline-player lookup)
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getUsername()));
        meta.displayName(name);
        meta.lore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    // -------------------------------------------------------------------------
    // Slot layout helper
    // -------------------------------------------------------------------------

    /**
     * Computes the 21 centred display slots:
     * rows 2-4 (0-indexed), columns 1-7 (0-indexed) → absolute slots 10-16,
     * 19-25, 28-34.
     */
    private static int[] buildDisplaySlots() {
        int[] slots = new int[21];
        int idx = 0;
        for (int row = 1; row <= 3; row++) {           // rows 2, 3, 4 (0-based 1,2,3)
            for (int col = 1; col <= 7; col++) {        // columns 1-7 (0-based)
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }
}
