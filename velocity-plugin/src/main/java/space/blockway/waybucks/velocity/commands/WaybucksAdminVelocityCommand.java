package space.blockway.waybucks.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import space.blockway.waybucks.velocity.api.WaybucksApiKeyAuthFilter;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;
import space.blockway.waybucks.velocity.database.WaybucksApiKeyRepository;
import space.blockway.waybucks.velocity.managers.ShopManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Velocity /wbadmin command for administrative management of the Waybucks plugin.
 *
 * <p>Permission node: {@code waybucks.admin}</p>
 */
public class WaybucksAdminVelocityCommand implements SimpleCommand {

    private static final String PERMISSION = "waybucks.admin";

    private static final String CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int KEY_SUFFIX_LENGTH = 32;

    private final ShopManager shopManager;
    private final WaybucksApiKeyRepository apiKeyRepo;
    private final WaybucksVelocityConfig config;
    private final Runnable reloadCallback;

    public WaybucksAdminVelocityCommand(ShopManager shopManager,
                                        WaybucksApiKeyRepository apiKeyRepo,
                                        WaybucksVelocityConfig config,
                                        Runnable reloadCallback) {
        this.shopManager = shopManager;
        this.apiKeyRepo = apiKeyRepo;
        this.config = config;
        this.reloadCallback = reloadCallback;
    }

    // -------------------------------------------------------------------------
    // Command execution
    // -------------------------------------------------------------------------

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission(PERMISSION)) {
            source.sendMessage(Component.text("You do not have permission to use this command.",
                    NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            sendUsage(source);
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> handleReload(source);
            case "apikey" -> handleApiKey(source, args);
            case "shop" -> handleShop(source, args);
            default -> sendUsage(source);
        }
    }

    // -------------------------------------------------------------------------
    // Sub-command: reload
    // -------------------------------------------------------------------------

    private void handleReload(CommandSource source) {
        try {
            reloadCallback.run();
            source.sendMessage(Component.text("[Waybucks] Configuration reloaded.", NamedTextColor.GREEN));
        } catch (Exception ex) {
            source.sendMessage(Component.text("[Waybucks] Failed to reload config: " + ex.getMessage(),
                    NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Sub-command: apikey
    // -------------------------------------------------------------------------

    private void handleApiKey(CommandSource source, String[] args) {
        // apikey generate <label>
        // apikey revoke <label>
        // apikey list
        if (args.length < 2) {
            source.sendMessage(Component.text(
                    "Usage: /wbadmin apikey <generate <label> | revoke <label> | list>",
                    NamedTextColor.YELLOW));
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "generate" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Usage: /wbadmin apikey generate <label>",
                            NamedTextColor.YELLOW));
                    return;
                }
                String label = args[2];
                String rawKey = "wb_" + generateRandomSuffix();
                String hash = WaybucksApiKeyAuthFilter.sha256(rawKey);
                apiKeyRepo.insertApiKey(label, hash);
                source.sendMessage(Component.text("[Waybucks] API key generated for label '", NamedTextColor.GREEN)
                        .append(Component.text(label, NamedTextColor.WHITE))
                        .append(Component.text("': ", NamedTextColor.GREEN))
                        .append(Component.text(rawKey, NamedTextColor.AQUA)));
                source.sendMessage(Component.text(
                        "[Waybucks] Save this key — it will NOT be shown again.", NamedTextColor.YELLOW));
            }

            case "revoke" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Usage: /wbadmin apikey revoke <label>",
                            NamedTextColor.YELLOW));
                    return;
                }
                String label = args[2];
                boolean deleted = apiKeyRepo.deleteApiKey(label);
                if (deleted) {
                    source.sendMessage(Component.text("[Waybucks] API key '", NamedTextColor.GREEN)
                            .append(Component.text(label, NamedTextColor.WHITE))
                            .append(Component.text("' revoked.", NamedTextColor.GREEN)));
                } else {
                    source.sendMessage(Component.text("[Waybucks] No API key found with label '",
                            NamedTextColor.RED)
                            .append(Component.text(label, NamedTextColor.WHITE))
                            .append(Component.text("'.", NamedTextColor.RED)));
                }
            }

            case "list" -> {
                List<WaybucksApiKeyRepository.ApiKeyRecord> keys = apiKeyRepo.listApiKeys();
                if (keys.isEmpty()) {
                    source.sendMessage(Component.text("[Waybucks] No API keys configured.",
                            NamedTextColor.YELLOW));
                    return;
                }
                source.sendMessage(Component.text("[Waybucks] API Keys:", NamedTextColor.GREEN));
                for (WaybucksApiKeyRepository.ApiKeyRecord key : keys) {
                    String lastUsed = key.lastUsed() != null ? key.lastUsed().toString() : "never";
                    source.sendMessage(Component.text("  - ", NamedTextColor.GRAY)
                            .append(Component.text(key.label(), NamedTextColor.WHITE))
                            .append(Component.text(" | created: ", NamedTextColor.GRAY))
                            .append(Component.text(String.valueOf(key.createdAt()), NamedTextColor.AQUA))
                            .append(Component.text(" | last used: ", NamedTextColor.GRAY))
                            .append(Component.text(lastUsed, NamedTextColor.AQUA)));
                }
            }

            default -> source.sendMessage(Component.text(
                    "Usage: /wbadmin apikey <generate <label> | revoke <label> | list>",
                    NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // Sub-command: shop
    // -------------------------------------------------------------------------

    private void handleShop(CommandSource source, String[] args) {
        // shop list
        // shop enable <id>
        // shop disable <id>
        // shop stock <id> <amount>
        // shop delete <id>
        if (args.length < 2) {
            sendShopUsage(source);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list" -> {
                var shops = shopManager.getAllShops();
                if (shops.isEmpty()) {
                    source.sendMessage(Component.text("[Waybucks] No shops configured.", NamedTextColor.YELLOW));
                    return;
                }
                source.sendMessage(Component.text("[Waybucks] Shops:", NamedTextColor.GREEN));
                for (var shop : shops) {
                    String status = shop.isEnabled() ? "enabled" : "disabled";
                    NamedTextColor statusColor = shop.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED;
                    source.sendMessage(Component.text("  - ", NamedTextColor.GRAY)
                            .append(Component.text(shop.getId(), NamedTextColor.WHITE))
                            .append(Component.text(" | " + shop.getName(), NamedTextColor.AQUA))
                            .append(Component.text(" | price: ", NamedTextColor.GRAY))
                            .append(Component.text(String.valueOf(shop.getPrice()), NamedTextColor.YELLOW))
                            .append(Component.text(" | stock: ", NamedTextColor.GRAY))
                            .append(Component.text(String.valueOf(shop.getStock()), NamedTextColor.YELLOW))
                            .append(Component.text(" [" + status + "]", statusColor)));
                }
            }

            case "enable", "disable" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Usage: /wbadmin shop " + action + " <id>",
                            NamedTextColor.YELLOW));
                    return;
                }
                String id = args[2];
                boolean enable = action.equals("enable");
                ShopManager.ShopResult result = shopManager.setEnabled(id, enable);
                if (result == ShopManager.ShopResult.SUCCESS) {
                    source.sendMessage(Component.text("[Waybucks] Shop '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.WHITE))
                            .append(Component.text("' " + (enable ? "enabled" : "disabled") + ".",
                                    NamedTextColor.GREEN)));
                } else if (result == ShopManager.ShopResult.SHOP_NOT_FOUND) {
                    source.sendMessage(Component.text("[Waybucks] Shop '", NamedTextColor.RED)
                            .append(Component.text(id, NamedTextColor.WHITE))
                            .append(Component.text("' not found.", NamedTextColor.RED)));
                } else {
                    source.sendMessage(Component.text("[Waybucks] Failed to update shop: " + result.name(),
                            NamedTextColor.RED));
                }
            }

            case "stock" -> {
                if (args.length < 4) {
                    source.sendMessage(Component.text("Usage: /wbadmin shop stock <id> <amount>",
                            NamedTextColor.YELLOW));
                    return;
                }
                String id = args[2];
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    source.sendMessage(Component.text("[Waybucks] Invalid amount: " + args[3],
                            NamedTextColor.RED));
                    return;
                }
                var shop = shopManager.getShop(id);
                if (shop == null) {
                    source.sendMessage(Component.text("[Waybucks] Shop '", NamedTextColor.RED)
                            .append(Component.text(id, NamedTextColor.WHITE))
                            .append(Component.text("' not found.", NamedTextColor.RED)));
                    return;
                }
                shop.setStock(amount);
                ShopManager.ShopResult result = shopManager.updateShop(shop);
                if (result == ShopManager.ShopResult.SUCCESS) {
                    source.sendMessage(Component.text("[Waybucks] Shop '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.WHITE))
                            .append(Component.text("' stock set to " + amount + ".", NamedTextColor.GREEN)));
                } else {
                    source.sendMessage(Component.text("[Waybucks] Failed to update stock: " + result.name(),
                            NamedTextColor.RED));
                }
            }

            case "delete" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Usage: /wbadmin shop delete <id>",
                            NamedTextColor.YELLOW));
                    return;
                }
                String id = args[2];
                ShopManager.ShopResult result = shopManager.deleteShop(id);
                if (result == ShopManager.ShopResult.SUCCESS) {
                    source.sendMessage(Component.text("[Waybucks] Shop '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.WHITE))
                            .append(Component.text("' deleted.", NamedTextColor.GREEN)));
                } else if (result == ShopManager.ShopResult.SHOP_NOT_FOUND) {
                    source.sendMessage(Component.text("[Waybucks] Shop '", NamedTextColor.RED)
                            .append(Component.text(id, NamedTextColor.WHITE))
                            .append(Component.text("' not found.", NamedTextColor.RED)));
                } else {
                    source.sendMessage(Component.text("[Waybucks] Failed to delete shop: " + result.name(),
                            NamedTextColor.RED));
                }
            }

            default -> sendShopUsage(source);
        }
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            for (String s : List.of("reload", "apikey", "shop")) {
                if (s.startsWith(partial)) suggestions.add(s);
            }
            return CompletableFuture.completedFuture(suggestions);
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("apikey") && args.length == 2) {
            String partial = args[1].toLowerCase();
            for (String s : List.of("generate", "revoke", "list")) {
                if (s.startsWith(partial)) suggestions.add(s);
            }
        } else if (sub.equals("apikey") && args.length == 3 && args[1].equalsIgnoreCase("revoke")) {
            String partial = args[2].toLowerCase();
            apiKeyRepo.listApiKeys().stream()
                    .map(WaybucksApiKeyRepository.ApiKeyRecord::label)
                    .filter(l -> l.toLowerCase().startsWith(partial))
                    .forEach(suggestions::add);
        } else if (sub.equals("shop") && args.length == 2) {
            String partial = args[1].toLowerCase();
            for (String s : List.of("list", "enable", "disable", "stock", "delete")) {
                if (s.startsWith(partial)) suggestions.add(s);
            }
        } else if (sub.equals("shop") && args.length == 3) {
            String action = args[1].toLowerCase();
            if (List.of("enable", "disable", "stock", "delete").contains(action)) {
                String partial = args[2].toLowerCase();
                shopManager.getAllShops().stream()
                        .map(space.blockway.waybucks.shared.dto.ShopDto::getId)
                        .filter(id -> id.toLowerCase().startsWith(partial))
                        .forEach(suggestions::add);
            }
        }

        return CompletableFuture.completedFuture(suggestions);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendUsage(CommandSource source) {
        source.sendMessage(Component.text("[Waybucks] Admin Commands:", NamedTextColor.GOLD));
        source.sendMessage(Component.text("  /wbadmin reload", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("  /wbadmin apikey <generate|revoke|list>", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("  /wbadmin shop <list|enable|disable|stock|delete>", NamedTextColor.YELLOW));
    }

    private void sendShopUsage(CommandSource source) {
        source.sendMessage(Component.text(
                "Usage: /wbadmin shop <list | enable <id> | disable <id> | stock <id> <amount> | delete <id>>",
                NamedTextColor.YELLOW));
    }

    private static String generateRandomSuffix() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(KEY_SUFFIX_LENGTH);
        for (int i = 0; i < KEY_SUFFIX_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
