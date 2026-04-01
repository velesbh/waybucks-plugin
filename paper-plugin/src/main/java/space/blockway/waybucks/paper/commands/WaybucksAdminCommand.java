package space.blockway.waybucks.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import space.blockway.waybucks.paper.WaybucksPaper;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles the {@code /waybucksadmin} command.
 *
 * <p>Requires the {@code waybucks.admin} permission.  Sub-commands:</p>
 * <ul>
 *   <li>{@code set <player> <amount>}   — set a player's balance</li>
 *   <li>{@code add <player> <amount>}   — add to a player's balance</li>
 *   <li>{@code take <player> <amount>}  — deduct from a player's balance</li>
 *   <li>{@code shop list}               — request shop list refresh from proxy</li>
 *   <li>{@code shop enable <id>}        — unsupported; proxy-side only</li>
 *   <li>{@code shop disable <id>}       — unsupported; proxy-side only</li>
 *   <li>{@code reload}                  — reload plugin config</li>
 * </ul>
 */
public class WaybucksAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "waybucks.admin";
    private static final List<String> TOP_LEVEL = List.of("set", "add", "take", "shop", "reload");
    private static final List<String> SHOP_SUBS = List.of("list", "enable", "disable");

    private final WaybucksPaper plugin;
    private final WaybucksProxyMessageSender sender;

    public WaybucksAdminCommand(WaybucksPaper plugin, WaybucksProxyMessageSender sender) {
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

        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "set"    -> handleBalanceOp(player, args, "set");
            case "add"    -> handleBalanceOp(player, args, "add");
            case "take"   -> handleBalanceOp(player, args, "take");
            case "shop"   -> handleShop(player, args);
            case "reload" -> handleReload(player);
            default       -> { sendUsage(player); yield true; }
        };
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    /**
     * Handles set/add/take: resolves online player → UUID → relay to proxy.
     */
    private boolean handleBalanceOp(Player admin, String[] args, String op) {
        if (args.length < 3) {
            admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage(
                    "admin-usage-" + op));
            return true;
        }

        String targetName = args[1];
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage("invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage("invalid-amount"));
            return true;
        }

        // Resolve UUID from online players
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage("player-not-found"));
            return true;
        }

        String targetUuid = target.getUniqueId().toString();

        switch (op) {
            case "set"  -> sender.sendAdminSet(admin, targetUuid, amount);
            case "add"  -> sender.sendAdminAdd(admin, targetUuid, amount);
            case "take" -> sender.sendAdminTake(admin, targetUuid, amount);
        }

        admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage(
                "admin-op-sent",
                "op", op,
                "player", targetName,
                "amount", String.valueOf(amount)
        ));
        return true;
    }

    /**
     * Handles the {@code shop} sub-command family.
     */
    private boolean handleShop(Player admin, String[] args) {
        if (args.length < 2) {
            admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage("admin-usage-shop"));
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> {
                admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage("shop-loading"));
                sender.sendShopListRequest(admin);
            }
            case "enable", "disable" -> admin.sendMessage(
                    plugin.getBwsConfig().getPrefixedMessage("admin-shop-use-proxy")
            );
            default -> admin.sendMessage(
                    plugin.getBwsConfig().getPrefixedMessage("admin-usage-shop")
            );
        }
        return true;
    }

    /**
     * Reloads the plugin configuration.
     */
    private boolean handleReload(Player admin) {
        plugin.reloadConfig();
        admin.sendMessage(plugin.getBwsConfig().getPrefixedMessage("config-reloaded"));
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(plugin.getBwsConfig().getPrefixedMessage("admin-usage"));
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command,
                                      String label, String[] args) {

        if (!(commandSender instanceof Player player)
                || !player.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filterStartsWith(TOP_LEVEL, args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "set", "add", "take" -> {
                    // Online player names
                    List<String> names = new ArrayList<>();
                    for (Player online : plugin.getServer().getOnlinePlayers()) {
                        if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            names.add(online.getName());
                        }
                    }
                    yield names;
                }
                case "shop" -> filterStartsWith(SHOP_SUBS, args[1]);
                default     -> List.of();
            };
        }

        if (args.length == 3 && List.of("set", "add", "take").contains(args[0].toLowerCase())) {
            return List.of("<amount>");
        }

        return List.of();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> filterStartsWith(List<String> source, String partial) {
        List<String> result = new ArrayList<>();
        String lower = partial.toLowerCase();
        for (String s : source) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}
