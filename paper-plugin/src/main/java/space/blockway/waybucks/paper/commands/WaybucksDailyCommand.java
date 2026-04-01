package space.blockway.waybucks.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;

/**
 * Handles the {@code /waybucksdaily} command.
 *
 * <p>Relays a DAILY_CLAIM_RELAY message to the proxy.  The proxy processes the
 * cooldown and streak logic, then responds with a DAILY_CLAIM_RESULT which is
 * handled by {@code WaybucksPaperMessageHandler}.</p>
 */
public class WaybucksDailyCommand implements CommandExecutor {

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    public WaybucksDailyCommand(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
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

        player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("daily-claiming"));
        sender.sendDailyClaim(player);
        return true;
    }
}
