package space.blockway.waybucks.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import space.blockway.waybucks.paper.commands.WaybucksAdminCommand;
import space.blockway.waybucks.paper.commands.WaybucksCommand;
import space.blockway.waybucks.paper.commands.WaybucksDailyCommand;
import space.blockway.waybucks.paper.commands.WaybucksItemCommand;
import space.blockway.waybucks.paper.commands.WaybucksLeaderboardCommand;
import space.blockway.waybucks.paper.commands.WaybucksPayCommand;
import space.blockway.waybucks.paper.commands.WaybucksShopCommand;
import space.blockway.waybucks.paper.config.WaybucksPaperConfig;
import space.blockway.waybucks.paper.gui.WaybucksGuiManager;
import space.blockway.waybucks.paper.listeners.WaybucksInventoryListener;
import space.blockway.waybucks.paper.listeners.WaybucksPlayerListener;
import space.blockway.waybucks.paper.messaging.WaybucksPaperMessageHandler;
import space.blockway.waybucks.paper.messaging.WaybucksProxyMessageSender;
import space.blockway.waybucks.shared.dto.ShopDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main plugin class for Waybucks on Paper 1.21.4.
 *
 * <p>Bootstraps configuration, messaging, GUIs, listeners, and commands on
 * enable; tears them down cleanly on disable.</p>
 */
public class WaybucksPaper extends JavaPlugin {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private WaybucksPaperConfig bwsConfig;
    private WaybucksProxyMessageSender messageSender;
    private WaybucksPaperMessageHandler messageHandler;
    private WaybucksGuiManager guiManager;

    /** Thread-safe balance cache: player UUID → current balance. */
    private final Map<UUID, Long> balanceCache = new ConcurrentHashMap<>();

    /** Thread-safe copy of the latest shop list received from Velocity. */
    private final List<ShopDto> shopCache = new CopyOnWriteArrayList<>();

    /** PersistentDataContainer key used to tag Waybucks physical note items. */
    private NamespacedKey waybucksAmountKey;

    // -------------------------------------------------------------------------
    // JavaPlugin lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {

        // 1. Configuration
        saveDefaultConfig();
        bwsConfig = new WaybucksPaperConfig(getConfig());

        // 2. Persistent-data key
        waybucksAmountKey = new NamespacedKey(this, "amount");

        // 3. Messaging components
        messageSender  = new WaybucksProxyMessageSender(this);
        messageHandler = new WaybucksPaperMessageHandler(this);

        // 4. GUI manager
        guiManager = new WaybucksGuiManager(this);

        // 5. Plugin-messaging channels
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, WaybucksProxyMessageSender.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, WaybucksProxyMessageSender.CHANNEL, messageHandler);

        // 6. Listeners
        getServer().getPluginManager().registerEvents(
                new WaybucksInventoryListener(guiManager), this);
        getServer().getPluginManager().registerEvents(
                new WaybucksPlayerListener(this, messageSender), this);

        // 7. Commands
        registerCommand("waybucks",         new WaybucksCommand(this, messageSender));
        registerCommand("waybuckspay",      new WaybucksPayCommand(this, messageSender));
        registerCommand("waybucksshop",     new WaybucksShopCommand(this, messageSender));
        registerCommand("waybucksdaily",    new WaybucksDailyCommand(this, messageSender));
        registerCommand("waybucksleaderboard", new WaybucksLeaderboardCommand(this, messageSender));
        registerCommand("waybucksitem",     new WaybucksItemCommand(this, messageSender));
        registerCommand("waybucksadmin",    new WaybucksAdminCommand(this, messageSender));

        // 8. Periodic shop-cache refresh every 5 minutes (6000 ticks)
        //    Uses the first available online player as the message carrier.
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            Collection<? extends Player> online = getServer().getOnlinePlayers();
            if (!online.isEmpty()) {
                Player carrier = online.iterator().next();
                // Switch to main thread for plugin-message sending
                getServer().getScheduler().runTask(this,
                        () -> messageSender.sendShopListRequest(carrier));
            }
        }, 6000L, 6000L);

        getLogger().info("Waybucks Paper plugin enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(
                this, WaybucksProxyMessageSender.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(
                this, WaybucksProxyMessageSender.CHANNEL);

        balanceCache.clear();
        shopCache.clear();

        getLogger().info("Waybucks Paper plugin disabled.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Registers a command executor (and optional tab-completer) for a named
     * command.  The command must be declared in {@code plugin.yml}.
     */
    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml — skipping.");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) {
            cmd.setExecutor(ce);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** Returns the typed Paper configuration wrapper. */
    public WaybucksPaperConfig getBwsConfig() {
        return bwsConfig;
    }

    /** Returns the proxy message sender. */
    public WaybucksProxyMessageSender getMessageSender() {
        return messageSender;
    }

    /** Returns the GUI manager. */
    public WaybucksGuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Returns the live balance cache.
     * Thread-safe: backed by {@link ConcurrentHashMap}.
     */
    public Map<UUID, Long> getBalanceCache() {
        return balanceCache;
    }

    /**
     * Returns the live shop cache.
     * Thread-safe: backed by {@link CopyOnWriteArrayList}.
     */
    public List<ShopDto> getShopCache() {
        return shopCache;
    }

    /** Returns the PersistentDataContainer key for Waybucks note items. */
    public NamespacedKey getWaybucksAmountKey() {
        return waybucksAmountKey;
    }
}
