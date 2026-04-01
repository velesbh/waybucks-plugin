package space.blockway.waybucks.velocity.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import space.blockway.waybucks.shared.dto.ShopDto;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ShopRepository {

    private static final Type LIST_OF_STRING = new TypeToken<List<String>>() {}.getType();
    private static final Gson GSON = new Gson();

    private final WaybucksDatabaseManager dbManager;

    public ShopRepository(WaybucksDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void createShop(ShopDto dto) throws SQLException {
        String sql = "INSERT INTO wb_shops (id, name, description, price, commands, stock, max_per_player, enabled, created_by, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dto.getId());
            ps.setString(2, dto.getName());
            ps.setString(3, dto.getDescription() != null ? dto.getDescription() : "");
            ps.setLong(4, dto.getPrice());
            ps.setString(5, GSON.toJson(dto.getCommands()));
            ps.setInt(6, dto.getStock());
            ps.setInt(7, dto.getMaxPerPlayer());
            ps.setBoolean(8, dto.isEnabled());
            ps.setString(9, dto.getCreatedBy() != null ? dto.getCreatedBy() : "api");
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public void updateShop(ShopDto dto) throws SQLException {
        String sql = "UPDATE wb_shops SET name = ?, description = ?, price = ?, commands = ?, " +
                     "stock = ?, max_per_player = ?, enabled = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dto.getName());
            ps.setString(2, dto.getDescription() != null ? dto.getDescription() : "");
            ps.setLong(3, dto.getPrice());
            ps.setString(4, GSON.toJson(dto.getCommands()));
            ps.setInt(5, dto.getStock());
            ps.setInt(6, dto.getMaxPerPlayer());
            ps.setBoolean(7, dto.isEnabled());
            ps.setString(8, dto.getId());
            ps.executeUpdate();
        }
    }

    public void deleteShop(String shopId) throws SQLException {
        String sql = "DELETE FROM wb_shops WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shopId);
            ps.executeUpdate();
        }
    }

    public Optional<ShopDto> getShop(String shopId) throws SQLException {
        String sql = "SELECT id, name, description, price, commands, stock, max_per_player, enabled, created_by, created_at " +
                     "FROM wb_shops WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<ShopDto> getAllShops() throws SQLException {
        String sql = "SELECT id, name, description, price, commands, stock, max_per_player, enabled, created_by, created_at " +
                     "FROM wb_shops ORDER BY created_at ASC";
        List<ShopDto> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public List<ShopDto> getEnabledShops() throws SQLException {
        String sql = "SELECT id, name, description, price, commands, stock, max_per_player, enabled, created_by, created_at " +
                     "FROM wb_shops WHERE enabled = 1 ORDER BY created_at ASC";
        List<ShopDto> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public int getPurchaseCount(String shopId, UUID playerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM wb_shop_purchases WHERE shop_id = ? AND player_uuid = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shopId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public int getTotalPurchaseCount(String shopId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM wb_shop_purchases WHERE shop_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public void recordPurchase(String shopId, UUID playerUuid) throws SQLException {
        String sql = "INSERT INTO wb_shop_purchases (shop_id, player_uuid, bought_at) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shopId);
            ps.setString(2, playerUuid.toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private ShopDto mapRow(ResultSet rs) throws SQLException {
        ShopDto dto = new ShopDto();
        dto.setId(rs.getString("id"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setPrice(rs.getLong("price"));
        dto.setCreatedBy(rs.getString("created_by"));
        dto.setCreatedAt(rs.getLong("created_at"));

        String commandsJson = rs.getString("commands");
        List<String> commands = GSON.fromJson(commandsJson, LIST_OF_STRING);
        dto.setCommands(commands != null ? commands : new ArrayList<>());

        dto.setStock(rs.getInt("stock"));
        dto.setMaxPerPlayer(rs.getInt("max_per_player"));
        dto.setEnabled(rs.getBoolean("enabled"));

        return dto;
    }
}
