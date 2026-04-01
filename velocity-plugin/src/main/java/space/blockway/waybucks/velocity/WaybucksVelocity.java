package space.blockway.waybucks.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import space.blockway.waybucks.velocity.api.WaybucksRestApiServer;
import space.blockway.waybucks.velocity.commands.WaybucksAdminVelocityCommand;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;
import space.blockway.waybucks.velocity.database.BalanceRepository;
import space.blockway.waybucks.velocity.database.DailyRepository;
import space.blockway.waybucks.velocity.database.ShopRepository;
import space.blockway.waybucks.velocity.database.TransactionRepository;
import space.blockway.waybucks.velocity.database.WaybucksApiKeyRepository;
import space.blockway.waybucks.velocity.database.WaybucksDatabaseManager;
import space.blockway.waybucks.velocity.managers.BalanceManager;
import space.blockway.waybucks.velocity.managers.DailyManager;
import space.blockway.waybucks.velocity.managers.ShopManager;
import space.blockway.waybucks.velocity.messaging.WaybucksMessageHandler;
import space.blockway.waybucks.velocity.messaging.WaybucksMessageSender;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "waybucks",
        name = "Waybucks",
        version = "1.0.0",
        description = "Waybucks currency system",
        url = "https://blockway.space",
        authors = {"Enzonic"}
)
public class WaybucksVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    // Lazily initialised in onProxyInitialize
    private WaybucksVelocityConfig config;
    private WaybucksDatabaseManager db;
    private WaybucksRestApiServer restApiServer;

    @Inject
    public WaybucksVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("[Waybucks] Initialising Waybucks v1.0.0…");

        // 1. Load configuration
        config = new WaybucksVelocityConfig(dataDirectory, logger);
        config.load();

        // 2. Initialise database & run migrations
        db = new WaybucksDatabaseManager(config, logger);
        try {
            db.initialize();
        } catch (Exception ex) {
            logger.error("[Waybucks] Failed to initialize database: {}", ex.getMessage(), ex);
            return;
        }

        // 3. Create repositories
        BalanceRepository balanceRepo = new BalanceRepository(db, config);
        TransactionRepository txRepo = new TransactionRepository(db);
        ShopRepository shopRepo = new ShopRepository(db);
        DailyRepository dailyRepo = new DailyRepository(db);
        WaybucksApiKeyRepository apiKeyRepo = new WaybucksApiKeyRepository(db);

        // 4. Create managers
        BalanceManager balanceManager = new BalanceManager(balanceRepo, txRepo, config, logger);
        ShopManager shopManager = new ShopManager(shopRepo, balanceRepo, txRepo, config, proxy, logger);
        DailyManager dailyManager = new DailyManager(dailyRepo, balanceRepo, txRepo, config, logger);

        // 5. Create plugin-message sender and register the channel
        WaybucksMessageSender messageSender = new WaybucksMessageSender(proxy, logger);
        proxy.getChannelRegistrar().register(WaybucksMessageSender.CHANNEL);

        // 6. Register plugin-message handler (Velocity event listener)
        WaybucksMessageHandler messageHandler = new WaybucksMessageHandler(
                balanceManager, shopManager, dailyManager, messageSender, config, logger);
        proxy.getEventManager().register(this, messageHandler);

        // 7. Register admin command
        WaybucksAdminVelocityCommand adminCommand = new WaybucksAdminVelocityCommand(
                shopManager,
                apiKeyRepo,
                config,
                () -> config.load()
        );
        proxy.getCommandManager().register(
                proxy.getCommandManager()
                        .metaBuilder("wbadmin")
                        .aliases("waybucksadmin")
                        .plugin(this)
                        .build(),
                adminCommand
        );

        // 8. Start REST API if enabled
        if (config.isApiEnabled()) {
            restApiServer = new WaybucksRestApiServer(
                    config, db, balanceManager, shopManager, txRepo, apiKeyRepo, logger);
            try {
                restApiServer.start();
            } catch (Exception ex) {
                logger.error("[Waybucks] Failed to start REST API server: {}", ex.getMessage(), ex);
            }
        } else {
            logger.info("[Waybucks] REST API is disabled in config.");
        }

        // 9. Schedule maintenance task — prune transactions older than 30 days, every hour
        final long thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L;
        proxy.getScheduler()
                .buildTask(this, () -> {
                    long cutoff = System.currentTimeMillis() - thirtyDaysMs;
                    try (Connection conn = db.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "DELETE FROM wb_transactions WHERE created_at < ?")) {
                        ps.setLong(1, cutoff);
                        int deleted = ps.executeUpdate();
                        if (deleted > 0) {
                            logger.info("[Waybucks] Pruned {} transaction(s) older than 30 days.", deleted);
                        }
                    } catch (Exception ex) {
                        logger.warn("[Waybucks] Failed to prune old transactions: {}", ex.getMessage(), ex);
                    }
                })
                .delay(1L, TimeUnit.HOURS)
                .repeat(1L, TimeUnit.HOURS)
                .schedule();

        logger.info("[Waybucks] Waybucks initialised successfully.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("[Waybucks] Shutting down Waybucks…");

        if (restApiServer != null) {
            restApiServer.stop();
        }

        if (db != null) {
            db.close();
        }

        logger.info("[Waybucks] Waybucks shut down.");
    }
}
