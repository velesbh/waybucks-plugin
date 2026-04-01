package space.blockway.waybucks.velocity.api.handlers;

import io.javalin.http.Context;
import space.blockway.waybucks.shared.dto.ShopDto;
import space.blockway.waybucks.velocity.managers.ShopManager;

import java.util.List;
import java.util.Map;

/**
 * REST handler for shop management operations.
 */
public class WaybucksShopHandler {

    private final ShopManager shopManager;

    public WaybucksShopHandler(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/shops[?enabled=true]
    // -------------------------------------------------------------------------

    /**
     * Returns all shops. If the query parameter {@code enabled=true} is present, only
     * enabled shops are returned.
     */
    public void listShops(Context ctx) {
        String enabledParam = ctx.queryParam("enabled");
        List<ShopDto> shops;

        if ("true".equalsIgnoreCase(enabledParam)) {
            shops = shopManager.getEnabledShops();
        } else {
            shops = shopManager.getAllShops();
        }

        ctx.json(shops);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/shops/{id}
    // -------------------------------------------------------------------------

    /**
     * Returns a single shop by its identifier.
     */
    public void getShop(Context ctx) {
        String id = ctx.pathParam("id");
        ShopDto shop = shopManager.getShop(id);

        if (shop == null) {
            ctx.status(404).json(Map.of("error", "Shop not found"));
            return;
        }

        ctx.json(shop);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/shops
    // Body: ShopDto JSON
    // -------------------------------------------------------------------------

    /**
     * Creates a new shop from the supplied {@link ShopDto}.
     */
    public void createShop(Context ctx) {
        ShopDto dto;
        try {
            dto = ctx.bodyAsClass(ShopDto.class);
        } catch (Exception ex) {
            ctx.status(400).json(Map.of("error", "Invalid shop payload: " + ex.getMessage()));
            return;
        }

        if (dto == null || dto.getId() == null || dto.getId().isBlank()) {
            ctx.status(400).json(Map.of("error", "Shop id is required"));
            return;
        }

        shopManager.createShop(dto);
        ctx.status(201).json(Map.of("result", "SUCCESS", "shopId", dto.getId()));
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/shops/{id}
    // Body: ShopDto JSON
    // -------------------------------------------------------------------------

    /**
     * Fully updates an existing shop.
     */
    public void updateShop(Context ctx) {
        String id = ctx.pathParam("id");

        ShopDto dto;
        try {
            dto = ctx.bodyAsClass(ShopDto.class);
        } catch (Exception ex) {
            ctx.status(400).json(Map.of("error", "Invalid shop payload: " + ex.getMessage()));
            return;
        }

        if (dto == null) {
            ctx.status(400).json(Map.of("error", "Request body is required"));
            return;
        }

        // Ensure the path id takes precedence
        dto.setId(id);

        ShopManager.ShopResult result = shopManager.updateShop(dto);

        if (result == ShopManager.ShopResult.SUCCESS) {
            ctx.json(Map.of("result", result.name()));
        } else if (result == ShopManager.ShopResult.SHOP_NOT_FOUND) {
            ctx.status(404).json(Map.of("result", result.name()));
        } else {
            ctx.status(400).json(Map.of("result", result.name()));
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/shops/{id}
    // -------------------------------------------------------------------------

    /**
     * Deletes a shop by identifier.
     */
    public void deleteShop(Context ctx) {
        String id = ctx.pathParam("id");
        ShopManager.ShopResult result = shopManager.deleteShop(id);

        if (result == ShopManager.ShopResult.SUCCESS) {
            ctx.json(Map.of("result", result.name()));
        } else if (result == ShopManager.ShopResult.SHOP_NOT_FOUND) {
            ctx.status(404).json(Map.of("result", result.name()));
        } else {
            ctx.status(400).json(Map.of("result", result.name()));
        }
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/shops/{id}/enabled
    // Body: { "enabled": <bool> }
    // -------------------------------------------------------------------------

    /**
     * Enables or disables a shop.
     */
    public void setEnabled(Context ctx) {
        String id = ctx.pathParam("id");

        SetEnabledBody body;
        try {
            body = ctx.bodyAsClass(SetEnabledBody.class);
        } catch (Exception ex) {
            ctx.status(400).json(Map.of("error", "Invalid payload: " + ex.getMessage()));
            return;
        }

        ShopManager.ShopResult result = shopManager.setEnabled(id, body.enabled);

        if (result == ShopManager.ShopResult.SUCCESS) {
            ctx.json(Map.of("result", result.name()));
        } else if (result == ShopManager.ShopResult.SHOP_NOT_FOUND) {
            ctx.status(404).json(Map.of("result", result.name()));
        } else {
            ctx.status(400).json(Map.of("result", result.name()));
        }
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/shops/{id}/stock
    // Body: { "stock": <int> }
    // -------------------------------------------------------------------------

    /**
     * Sets the stock level for a shop. A negative value typically represents unlimited stock
     * depending on the ShopManager implementation.
     */
    public void setStock(Context ctx) {
        String id = ctx.pathParam("id");

        SetStockBody body;
        try {
            body = ctx.bodyAsClass(SetStockBody.class);
        } catch (Exception ex) {
            ctx.status(400).json(Map.of("error", "Invalid payload: " + ex.getMessage()));
            return;
        }

        ShopDto existing = shopManager.getShop(id);
        if (existing == null) {
            ctx.status(404).json(Map.of("error", "Shop not found"));
            return;
        }

        existing.setStock(body.stock);
        ShopManager.ShopResult result = shopManager.updateShop(existing);

        if (result == ShopManager.ShopResult.SUCCESS) {
            ctx.json(Map.of("result", result.name(), "stock", body.stock));
        } else {
            ctx.status(400).json(Map.of("result", result.name()));
        }
    }

    // -------------------------------------------------------------------------
    // Request body POJOs
    // -------------------------------------------------------------------------

    public static class SetEnabledBody {
        public boolean enabled;
    }

    public static class SetStockBody {
        public int stock;
    }
}
