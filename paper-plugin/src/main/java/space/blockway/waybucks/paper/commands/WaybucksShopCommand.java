package space.blockway.waybucks.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;
import space.blockway.waybucks.shared.dto.ShopDto;

import java.util.List;

/**
 * Handles the {@code /waybucksshop} command.
 *
 * <p>Strategy for obtaining the shop list:</p>
 * <ol>
 *   <li>If the plugin already has a non-empty shop cache, open the GUI immediately
 *       using the cached data.</li>
 *   <li>Otherwise, send a LEADERBOARD_REQUEST with {@code requestType = "SHOPS"}
 *       to the proxy.  The proxy replies with a LEADERBOARD_RESPONSE containing a
 *       {@code shops} JSON array, which {@code WaybucksPaperMessageHandler}
 *       intercepts to update the cache and open the GUI.</li>
 * </ol>
 */
public class WaybucksShopCommand implements CommandExecutor {

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    public WaybucksShopCommand(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    // -------------------------------------------------------------------------
    // CommandExecutor
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender commandSender, Command command,
                             String label, String[] args) {

        if (!(commandSender instanceof Player player)) {
            commandSender.sendMessage("This command can only be used by players.");
            return true;
        }

        List<ShopDto> cached = plugin.getShopCache();

        if (!cached.isEmpty()) {
            // Open immediately from cache
            plugin.getGuiManager().openShopGui(player, List.copyOf(cached));
        } else {
            // Request from proxy; GUI will open once the response arrives
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("shop-loading"));
            sender.sendShopListRequest(player);
        }

        return true;
    }
}
