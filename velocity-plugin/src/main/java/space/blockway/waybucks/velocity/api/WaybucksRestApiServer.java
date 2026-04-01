package space.blockway.waybucks.velocity.api;

import io.javalin.Javalin;
import org.slf4j.Logger;
import space.blockway.waybucks.velocity.api.handlers.WaybucksAdminApiHandler;
import space.blockway.waybucks.velocity.api.handlers.WaybucksBalanceHandler;
import space.blockway.waybucks.velocity.api.handlers.WaybucksLeaderboardHandler;
import space.blockway.waybucks.velocity.api.handlers.WaybucksShopHandler;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;
import space.blockway.waybucks.velocity.database.TransactionRepository;
import space.blockway.waybucks.velocity.database.WaybucksApiKeyRepository;
import space.blockway.waybucks.velocity.database.WaybucksDatabaseManager;
import space.blockway.waybucks.velocity.managers.BalanceManager;
import space.blockway.waybucks.velocity.managers.ShopManager;

import java.util.Map;

/**
 * Manages the lifecycle of the embedded Javalin HTTP server that exposes the Waybucks REST API.
 *
 * <p>The Javalin server is started on a configurable host/port. A classloader swap is performed
 * before creating the Javalin instance so that Javalin's service-loader mechanisms find the
 * correct implementations even when running inside a plugin container.</p>
 */
public class WaybucksRestApiServer {

    private final WaybucksVelocityConfig config;
    private final WaybucksDatabaseManager db;
    private final BalanceManager balanceManager;
    private final ShopManager shopManager;
    private final TransactionRepository txRepo;
    private final WaybucksApiKeyRepository apiKeyRepo;
    private final Logger logger;

    private Javalin javalin;

    public WaybucksRestApiServer(WaybucksVelocityConfig config,
                                 WaybucksDatabaseManager db,
                                 BalanceManager balanceManager,
                                 ShopManager shopManager,
                                 TransactionRepository txRepo,
                                 WaybucksApiKeyRepository apiKeyRepo,
                                 Logger logger) {
        this.config = config;
        this.db = db;
        this.balanceManager = balanceManager;
        this.shopManager = shopManager;
        this.txRepo = txRepo;
        this.apiKeyRepo = apiKeyRepo;
        this.logger = logger;
    }

    /**
     * Starts the REST API server.
     *
     * <p>The thread-context classloader is temporarily swapped to Javalin's classloader so that
     * service-loader and SPI look-ups (e.g. Jackson, Jetty) resolve correctly inside a plugin
     * environment.</p>
     */
    public void start() {
        String masterKey = config.getMasterKey();
        String bindHost = config.getApiBind();
        int port = config.getApiPort();

        // --- Classloader swap -----------------------------------------------------------
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Javalin.class.getClassLoader());

        try {
            WaybucksApiKeyAuthFilter authFilter = new WaybucksApiKeyAuthFilter(apiKeyRepo, masterKey);

            WaybucksBalanceHandler balanceHandler =
                    new WaybucksBalanceHandler(balanceManager, txRepo, db);
            WaybucksShopHandler shopHandler = new WaybucksShopHandler(shopManager);
            WaybucksLeaderboardHandler leaderboardHandler = new WaybucksLeaderboardHandler(balanceManager);
            WaybucksAdminApiHandler adminHandler = new WaybucksAdminApiHandler(apiKeyRepo, masterKey);

            javalin = Javalin.create(cfg -> {
                cfg.showJavalinBanner = false;
                cfg.jsonMapper(new io.javalin.json.JavalinGson());
            });

            javalin.before("/api/v1/*", authFilter);

            // --- Balance / transaction routes -------------------------------------------
            javalin.get("/api/v1/player/{username}/balance", balanceHandler::getBalance);
            javalin.get("/api/v1/player/{uuid}/transactions", balanceHandler::getTransactions);
            javalin.post("/api/v1/player/{uuid}/balance", balanceHandler::setBalance);
            javalin.post("/api/v1/player/{uuid}/balance/add", balanceHandler::addBalance);
            javalin.post("/api/v1/player/{uuid}/balance/take", balanceHandler::takeBalance);
            javalin.post("/api/v1/transfer", balanceHandler::transfer);

            // --- Shop routes ------------------------------------------------------------
            javalin.get("/api/v1/shops", shopHandler::listShops);
            javalin.get("/api/v1/shops/{id}", shopHandler::getShop);

            javalin.post("/api/v1/shops", ctx -> {
                requireMasterKey(ctx, shopHandler::createShop);
            });
            javalin.put("/api/v1/shops/{id}", ctx -> {
                requireMasterKey(ctx, shopHandler::updateShop);
            });
            javalin.delete("/api/v1/shops/{id}", ctx -> {
                requireMasterKey(ctx, shopHandler::deleteShop);
            });
            javalin.patch("/api/v1/shops/{id}/enabled", ctx -> {
                requireMasterKey(ctx, shopHandler::setEnabled);
            });
            javalin.patch("/api/v1/shops/{id}/stock", ctx -> {
                requireMasterKey(ctx, shopHandler::setStock);
            });

            // --- Leaderboard ------------------------------------------------------------
            javalin.get("/api/v1/leaderboard", leaderboardHandler::getLeaderboard);

            // --- API key management (master only) ---------------------------------------
            javalin.get("/api/v1/apikey", adminHandler::listApiKeys);
            javalin.post("/api/v1/apikey", adminHandler::generateApiKey);
            javalin.delete("/api/v1/apikey/{label}", adminHandler::revokeApiKey);

            // --- Generic exception handler ----------------------------------------------
            javalin.exception(Exception.class, (ex, ctx) -> {
                logger.error("[WaybucksRestApiServer] Unhandled exception on {} {}: {}",
                        ctx.method(), ctx.path(), ex.getMessage(), ex);
                ctx.status(500).json(Map.of("error", "Internal server error"));
            });

            javalin.start(bindHost, port);
            logger.info("[WaybucksRestApiServer] REST API listening on {}:{}", bindHost, port);

        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
        }
    }

    /**
     * Stops the Javalin server gracefully.
     */
    public void stop() {
        if (javalin != null) {
            javalin.stop();
            logger.info("[WaybucksRestApiServer] REST API stopped.");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Ensures that only requests authenticated with the master key reach admin-only handlers.
     */
    private void requireMasterKey(io.javalin.http.Context ctx,
                                   io.javalin.http.Handler handler) throws Exception {
        Boolean master = ctx.attribute("isMasterKey");
        if (master == null || !master) {
            ctx.status(403).json(Map.of("error", "Forbidden — master key required"));
            return;
        }
        handler.handle(ctx);
    }
}
