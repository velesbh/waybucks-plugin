package space.blockway.waybucks.velocity.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class DailyRepository {

    public record DailyRecord(UUID uuid, long lastClaimedAt, int streak) {}

    private final WaybucksDatabaseManager dbManager;

    public DailyRepository(WaybucksDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Optional<DailyRecord> getDailyRecord(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, last_claimed_at, streak FROM wb_daily WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID recordUuid = UUID.fromString(rs.getString("uuid"));
                    long lastClaimedAt = rs.getLong("last_claimed_at");
                    int streak = rs.getInt("streak");
                    return Optional.of(new DailyRecord(recordUuid, lastClaimedAt, streak));
                }
            }
        }
        return Optional.empty();
    }

    public void upsert(UUID uuid, long lastClaimedAt, int streak) throws SQLException {
        String sql;
        if (dbManager.isSqlite()) {
            sql = "INSERT OR REPLACE INTO wb_daily (uuid, last_claimed_at, streak) VALUES (?, ?, ?)";
        } else {
            sql = "REPLACE INTO wb_daily (uuid, last_claimed_at, streak) VALUES (?, ?, ?)";
        }

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, lastClaimedAt);
            ps.setInt(3, streak);
            ps.executeUpdate();
        }
    }
}
