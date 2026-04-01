package space.blockway.waybucks.paper.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the {@code /waybucks [player]} command.
 *
 * <ul>
 *   <li>No arguments: requests the executing player's own balance from the proxy.</li>
 *   <li>With a player argument: if that player is online, requests their balance;
 *       otherwise informs the sender that the player was not found.</li>
 * </ul>
 */
public class WaybucksCommand implements CommandExecutor, TabCompleter {

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    public WaybucksCommand(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
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

        if (args.length == 0) {
            // Own balance
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("fetching-balance"));
            sender.sendBalanceRequest(player);
            return true;
        }

        // Another player's balance
        String targetName = args[0];
        Player target = plugin.getServer().getPlayerExact(targetName);

        if (target == null) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("player-not-found"));
            return true;
        }

        player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("fetching-balance"));
        // Balance request is keyed to the *requesting* player; the proxy returns
        // the balance for the UUID embedded in the payload.
        sender.sendBalanceRequest(target);
        return true;
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(partial)) {
                    names.add(online.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
