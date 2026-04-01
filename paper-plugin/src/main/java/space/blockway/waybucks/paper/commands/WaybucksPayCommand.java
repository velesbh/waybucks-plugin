package space.blockway.waybucks.paper.commands;

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
 * Handles the {@code /waybuckspay <player> <amount>} command.
 *
 * <p>Validates the amount and relays the transfer to the proxy.  Velocity
 * resolves the recipient UUID from the provided username and responds with a
 * TRANSFER_RESULT.</p>
 */
public class WaybucksPayCommand implements CommandExecutor, TabCompleter {

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    public WaybucksPayCommand(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
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

        if (args.length < 2) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("usage-pay"));
            return true;
        }

        String targetName = args[0];
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("invalid-amount"));
            return true;
        }

        // Prevent self-transfers
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("cannot-pay-self"));
            return true;
        }

        player.sendMessage(plugin.getBwsConfig().getPrefixedMessage(
                "transfer-sending",
                "player", targetName,
                "amount", String.valueOf(amount)
        ));
        sender.sendTransfer(player, targetName, amount);
        return true;
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command,
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
