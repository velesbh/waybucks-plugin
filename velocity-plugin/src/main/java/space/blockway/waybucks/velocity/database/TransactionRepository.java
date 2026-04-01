package space.blockway.waybucks.velocity.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionRepository {

    public record TransactionRecord(
            long id,
            String type,
            UUID playerUuid,
            UUID targetUuid,
            long amount,
            String note,
            long createdAt
    ) {}

    private final WaybucksDatabaseManager dbManager;

    public TransactionRepository(WaybucksDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void record(String type, UUID playerUuid, UUID targetUuid, long amount, String note) throws SQLException {
        String sql = "INSERT INTO wb_transactions (type, player_uuid, target_uuid, amount, note, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, playerUuid != null ? playerUuid.toString() : null);
            ps.setString(3, targetUuid != null ? targetUuid.toString() : null);
            ps.setLong(4, amount);
            ps.setString(5, note);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<TransactionRecord> getTransactions(UUID playerUuid, int limit) throws SQLException {
        String sql = "SELECT id, type, player_uuid, target_uuid, amount, note, created_at " +
                     "FROM wb_transactions " +
                     "WHERE player_uuid = ? OR target_uuid = ? " +
                     "ORDER BY created_at DESC LIMIT ?";

        List<TransactionRecord> results = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String uuidStr = playerUuid.toString();
            ps.setString(1, uuidStr);
            ps.setString(2, uuidStr);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String type = rs.getString("type");

                    String playerUuidStr = rs.getString("player_uuid");
                    UUID pUuid = playerUuidStr != null ? UUID.fromString(playerUuidStr) : null;

                    String targetUuidStr = rs.getString("target_uuid");
                    UUID tUuid = targetUuidStr != null ? UUID.fromString(targetUuidStr) : null;

                    long amount = rs.getLong("amount");
                    String note = rs.getString("note");
                    long createdAt = rs.getLong("created_at");

                    results.add(new TransactionRecord(id, type, pUuid, tUuid, amount, note, createdAt));
                }
            }
        }

        return results;
    }
}
