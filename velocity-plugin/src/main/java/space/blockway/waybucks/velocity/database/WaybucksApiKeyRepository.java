package space.blockway.waybucks.velocity.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WaybucksApiKeyRepository {

    /** Record used by {@link #listApiKeys()}. */
    public record ApiKeyRecord(String label, Long lastUsed, long createdAt) {}

    private final WaybucksDatabaseManager dbManager;

    public WaybucksApiKeyRepository(WaybucksDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Inserts a new API key.
     *
     * @param label   human-readable label
     * @param keyHash SHA-256 hex hash of the raw key
     */
    public void insertApiKey(String label, String keyHash) {
        String sql = "INSERT INTO wb_api_keys (key_hash, label, created_at) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, keyHash);
            ps.setString(2, label);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert API key for label: " + label, e);
        }
    }

    /**
     * Validates a key hash and updates last_used on success.
     * Uses key_hash as the primary key (no separate id column).
     *
     * @param keyHash SHA-256 hex of the bearer token
     * @return true if the key exists and is active
     */
    public boolean validateAndTouch(String keyHash) {
        String selectSql = "SELECT 1 FROM wb_api_keys WHERE key_hash = ?";
        boolean found = false;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, keyHash);
            try (ResultSet rs = ps.executeQuery()) {
                found = rs.next();
            }
        } catch (SQLException e) {
            return false;
        }

        if (!found) return false;

        String updateSql = "UPDATE wb_api_keys SET last_used = ? WHERE key_hash = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, keyHash);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Non-fatal — key is still valid even if last_used update fails
        }

        return true;
    }

    /**
     * Deletes an API key by label.
     *
     * @return true if a row was deleted
     */
    public boolean deleteApiKey(String label) {
        String sql = "DELETE FROM wb_api_keys WHERE label = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, label);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Returns all API keys ordered by creation time.
     */
    public List<ApiKeyRecord> listApiKeys() {
        String sql = "SELECT label, last_used, created_at FROM wb_api_keys ORDER BY created_at ASC";
        List<ApiKeyRecord> results = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String label = rs.getString("label");
                long lastUsedRaw = rs.getLong("last_used");
                Long lastUsed = rs.wasNull() ? null : lastUsedRaw;
                long createdAt = rs.getLong("created_at");
                results.add(new ApiKeyRecord(label, lastUsed, createdAt));
            }
        } catch (SQLException e) {
            return List.of();
        }

        return results;
    }
}
