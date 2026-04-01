package space.blockway.waybucks.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;

/**
 * Handles the {@code /waybucksitem <amount>} command.
 *
 * <p>Converts Waybucks balance into a physical note item by sending an
 * ITEM_CONVERT_RELAY to the proxy.  On the proxy side the player's balance is
 * deducted; if successful a ITEM_CONVERT_RESULT is returned and
 * {@code WaybucksPaperMessageHandler} spawns the item in the player's inventory
 * on the main thread.</p>
 */
public class WaybucksItemCommand implements CommandExecutor {

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    public WaybucksItemCommand(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
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

        if (args.length < 1) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("usage-item"));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("invalid-amount"));
            return true;
        }

        player.sendMessage(plugin.getBwsConfig().getPrefixedMessage(
                "item-converting",
                "amount", String.valueOf(amount)
        ));
        sender.sendItemConvert(player, amount);
        return true;
    }
}
