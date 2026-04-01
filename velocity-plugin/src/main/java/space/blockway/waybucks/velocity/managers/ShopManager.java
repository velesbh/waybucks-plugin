package space.blockway.waybucks.velocity.managers;

import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import space.blockway.waybucks.shared.dto.ShopDto;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;
import space.blockway.waybucks.velocity.database.BalanceRepository;
import space.blockway.waybucks.velocity.database.ShopRepository;
import space.blockway.waybucks.velocity.database.TransactionRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ShopManager {

    public enum ShopResult {
        SUCCESS,
        SHOP_NOT_FOUND,
        INSUFFICIENT_FUNDS,
        OUT_OF_STOCK,
        MAX_REACHED,
        DISABLED,
        INVALID
    }

    private final ShopRepository shopRepository;
    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final WaybucksVelocityConfig config;
    private final ProxyServer proxy;
    private final Logger logger;

    public ShopManager(
            ShopRepository shopRepository,
            BalanceRepository balanceRepository,
            TransactionRepository transactionRepository,
            WaybucksVelocityConfig config,
            ProxyServer proxy,
            Logger logger
    ) {
        this.shopRepository = shopRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.config = config;
        this.proxy = proxy;
        this.logger = logger;
    }

    public void createShop(ShopDto dto) {
        try {
            shopRepository.createShop(dto);
        } catch (SQLException e) {
            logger.error("Failed to create shop '{}'", dto.getId(), e);
        }
    }

    public ShopResult updateShop(ShopDto dto) {
        try {
            shopRepository.updateShop(dto);
            return ShopResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to update shop '{}'", dto.getId(), e);
            return ShopResult.INVALID;
        }
    }

    public ShopResult deleteShop(String shopId) {
        try {
            Optional<ShopDto> existing = shopRepository.getShop(shopId);
            if (existing.isEmpty()) {
                return ShopResult.SHOP_NOT_FOUND;
            }
            shopRepository.deleteShop(shopId);
            return ShopResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to delete shop '{}'", shopId, e);
            return ShopResult.INVALID;
        }
    }

    public ShopDto getShop(String shopId) {
        try {
            return shopRepository.getShop(shopId).orElse(null);
        } catch (SQLException e) {
            logger.error("Failed to get shop '{}'", shopId, e);
            return null;
        }
    }

    public List<ShopDto> getAllShops() {
        try {
            return shopRepository.getAllShops();
        } catch (SQLException e) {
            logger.error("Failed to get all shops", e);
            return List.of();
        }
    }

    public List<ShopDto> getEnabledShops() {
        try {
            return shopRepository.getEnabledShops();
        } catch (SQLException e) {
            logger.error("Failed to get enabled shops", e);
            return List.of();
        }
    }

    public ShopResult purchase(UUID buyerUuid, String buyerName, String shopId) {
        try {
            // 1. Get shop
            Optional<ShopDto> optShop = shopRepository.getShop(shopId);
            if (optShop.isEmpty()) {
                return ShopResult.SHOP_NOT_FOUND;
            }
            ShopDto shop = optShop.get();

            // Check enabled
            if (!shop.isEnabled()) {
                return ShopResult.DISABLED;
            }

            // Check stock (-1 means unlimited, 0 means out of stock)
            if (shop.getStock() == 0) {
                return ShopResult.OUT_OF_STOCK;
            }

            // Check maxPerPlayer (-1 means unlimited)
            if (shop.getMaxPerPlayer() > 0) {
                int purchaseCount = shopRepository.getPurchaseCount(shopId, buyerUuid);
                if (purchaseCount >= shop.getMaxPerPlayer()) {
                    return ShopResult.MAX_REACHED;
                }
            }

            // 2. Deduct balance
            boolean deducted = balanceRepository.takeBalance(buyerUuid, shop.getPrice());
            if (!deducted) {
                return ShopResult.INSUFFICIENT_FUNDS;
            }

            // 3. Record purchase
            shopRepository.recordPurchase(shopId, buyerUuid);

            // Decrement stock if finite
            if (shop.getStock() > 0) {
                shop.setStock(shop.getStock() - 1);
                shopRepository.updateShop(shop);
            }

            // Log transaction
            transactionRepository.record("SHOP", buyerUuid, null, shop.getPrice(),
                    "Purchased shop item: " + shopId);

            // 4. Execute commands
            if (shop.getCommands() != null) {
                for (String cmd : shop.getCommands()) {
                    String resolved = cmd
                            .replace("{player}", buyerName)
                            .replace("{uuid}", buyerUuid.toString());
                    proxy.getCommandManager()
                            .executeImmediatelyAsync(proxy.getConsoleCommandSource(), resolved);
                }
            }

            return ShopResult.SUCCESS;

        } catch (SQLException e) {
            logger.error("Failed to process purchase of shop '{}' for player {}", shopId, buyerUuid, e);
            return ShopResult.INVALID;
        }
    }

    public ShopResult setEnabled(String shopId, boolean enabled) {
        try {
            Optional<ShopDto> optShop = shopRepository.getShop(shopId);
            if (optShop.isEmpty()) {
                return ShopResult.SHOP_NOT_FOUND;
            }
            ShopDto shop = optShop.get();
            shop.setEnabled(enabled);
            shopRepository.updateShop(shop);
            return ShopResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to set enabled={} for shop '{}'", enabled, shopId, e);
            return ShopResult.INVALID;
        }
    }

    public ShopResult setStock(String shopId, int stock) {
        try {
            Optional<ShopDto> optShop = shopRepository.getShop(shopId);
            if (optShop.isEmpty()) {
                return ShopResult.SHOP_NOT_FOUND;
            }
            ShopDto shop = optShop.get();
            shop.setStock(stock);
            shopRepository.updateShop(shop);
            return ShopResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to set stock={} for shop '{}'", stock, shopId, e);
            return ShopResult.INVALID;
        }
    }
}
