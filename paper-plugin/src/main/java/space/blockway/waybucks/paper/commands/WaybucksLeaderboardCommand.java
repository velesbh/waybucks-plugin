package space.blockway.waybucks.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;

/**
 * Handles the {@code /waybucksleaderboard} command.
 *
 * <p>Sends a LEADERBOARD_REQUEST (with {@code requestType = "LEADERBOARD"} and
 * {@code limit = 10}) to the proxy.  The proxy replies with a LEADERBOARD_RESPONSE
 * whose {@code entries} array is used by {@code WaybucksPaperMessageHandler} to
 * open the {@link space.blockway.waybucks.paper.gui.LeaderboardGui}.</p>
 */
public class WaybucksLeaderboardCommand implements CommandExecutor {

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    /** Default number of leaderboard entries to request. */
    private static final int DEFAULT_LIMIT = 10;

    public WaybucksLeaderboardCommand(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
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

        player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("leaderboard-loading"));
        sender.sendLeaderboardRequest(player, DEFAULT_LIMIT);
        return true;
    }
}
