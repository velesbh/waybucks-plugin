package space.blockway.waybucks.velocity.database;

import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BalanceRepository {

    public record BalanceRecord(UUID uuid, String username, long balance, long updatedAt) {}

    private final WaybucksDatabaseManager dbManager;
    private final WaybucksVelocityConfig config;

    public BalanceRepository(WaybucksDatabaseManager dbManager, WaybucksVelocityConfig config) {
        this.dbManager = dbManager;
        this.config = config;
    }

    public void upsertPlayer(UUID uuid, String username) throws SQLException {
        String sql;
        if (dbManager.isSqlite()) {
            sql = "INSERT OR IGNORE INTO wb_balances (uuid, username, balance, updated_at) VALUES (?, ?, ?, ?)";
        } else {
            sql = "INSERT IGNORE INTO wb_balances (uuid, username, balance, updated_at) VALUES (?, ?, ?, ?)";
        }

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, config.getStartingBalance());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }

        // Always update the username in case it changed
        String updateName = "UPDATE wb_balances SET username = ? WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateName)) {
            ps.setString(1, username);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public long getBalance(UUID uuid) throws SQLException {
        String sql = "SELECT balance FROM wb_balances WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("balance");
                }
            }
        }
        return 0L;
    }

    public void setBalance(UUID uuid, long amount) throws SQLException {
        String sql = "UPDATE wb_balances SET balance = ?, updated_at = ? WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void addBalance(UUID uuid, long amount) throws SQLException {
        String sql = "UPDATE wb_balances SET balance = MAX(0, balance + ?), updated_at = ? WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * Deducts amount from the player's balance, flooring at 0.
     * Returns false if the player has insufficient funds (balance < amount).
     */
    public boolean takeBalance(UUID uuid, long amount) throws SQLException {
        // First check balance
        long current = getBalance(uuid);
        if (current < amount) {
            return false;
        }

        String sql = "UPDATE wb_balances SET balance = MAX(0, balance - ?), updated_at = ? WHERE uuid = ? AND balance >= ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.setLong(4, amount);
            int rows = ps.executeUpdate();
            return rows > 0;
        }
    }

    /**
     * Atomically transfers amount from one player to another.
     * Returns false if the sender has insufficient funds.
     */
    public boolean transfer(UUID from, UUID to, long amount) throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // Check sender balance with a row-level check
                long senderBalance;
                String checkSql = "SELECT balance FROM wb_balances WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, from.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        senderBalance = rs.getLong("balance");
                    }
                }

                if (senderBalance < amount) {
                    conn.rollback();
                    return false;
                }

                // Deduct from sender
                String takeSql = "UPDATE wb_balances SET balance = balance - ?, updated_at = ? WHERE uuid = ? AND balance >= ?";
                try (PreparedStatement ps = conn.prepareStatement(takeSql)) {
                    ps.setLong(1, amount);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, from.toString());
                    ps.setLong(4, amount);
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // Add to receiver
                String addSql = "UPDATE wb_balances SET balance = balance + ?, updated_at = ? WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(addSql)) {
                    ps.setLong(1, amount);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, to.toString());
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    public List<BalanceRecord> getTopBalances(int limit) throws SQLException {
        String sql = "SELECT uuid, username, balance, updated_at FROM wb_balances ORDER BY balance DESC LIMIT ?";
        List<BalanceRecord> results = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String username = rs.getString("username");
                    long balance = rs.getLong("balance");
                    long updatedAt = rs.getLong("updated_at");
                    results.add(new BalanceRecord(uuid, username, balance, updatedAt));
                }
            }
        }

        return results;
    }
}
