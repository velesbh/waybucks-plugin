package space.blockway.waybucks.velocity.api.handlers;

import io.javalin.http.Context;
import space.blockway.waybucks.shared.dto.BalanceDto;
import space.blockway.waybucks.shared.dto.TransactionDto;
import space.blockway.waybucks.velocity.database.TransactionRepository;
import space.blockway.waybucks.velocity.database.WaybucksDatabaseManager;
import space.blockway.waybucks.velocity.managers.BalanceManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST handler for player balance and transaction operations.
 */
public class WaybucksBalanceHandler {

    private final BalanceManager balanceManager;
    private final TransactionRepository txRepo;
    private final WaybucksDatabaseManager db;

    public WaybucksBalanceHandler(BalanceManager balanceManager,
                                  TransactionRepository txRepo,
                                  WaybucksDatabaseManager db) {
        this.balanceManager = balanceManager;
        this.txRepo = txRepo;
        this.db = db;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/player/{username}/balance
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link BalanceDto} for the player with the given username.
     * The look-up is case-insensitive against {@code wb_balances}.
     */
    public void getBalance(Context ctx) {
        String username = ctx.pathParam("username");

        BalanceDto dto = null;
        String sql = "SELECT uuid, username, balance, updated_at FROM wb_balances WHERE lower(username) = lower(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto = new BalanceDto(
                            rs.getString("uuid"),
                            rs.getString("username"),
                            rs.getLong("balance"),
                            rs.getLong("updated_at")
                    );
                }
            }
        } catch (SQLException ex) {
            ctx.status(500).json(Map.of("error", "Database error"));
            return;
        }

        if (dto == null) {
            ctx.status(404).json(Map.of("error", "Player not found"));
            return;
        }

        ctx.json(dto);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/player/{uuid}/transactions?limit=20
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of transactions for the player.
     */
    public void getTransactions(Context ctx) {
        UUID uuid;
        try {
            uuid = UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(Map.of("error", "Invalid UUID"));
            return;
        }

        int limit = 20;
        String limitParam = ctx.queryParam("limit");
        if (limitParam != null) {
            try {
                limit = Integer.parseInt(limitParam);
                if (limit < 1 || limit > 200) limit = 20;
            } catch (NumberFormatException ignored) {
                // use default
            }
        }

        try {
            List<TransactionRepository.TransactionRecord> records = txRepo.getTransactions(uuid, limit);
            List<TransactionDto> dtos = new ArrayList<>(records.size());
            for (TransactionRepository.TransactionRecord r : records) {
                TransactionDto d = new TransactionDto();
                d.setId(r.id());
                d.setType(r.type());
                d.setPlayerUuid(r.playerUuid() != null ? r.playerUuid().toString() : null);
                d.setTargetUuid(r.targetUuid() != null ? r.targetUuid().toString() : null);
                d.setAmount(r.amount());
                d.setNote(r.note());
                d.setCreatedAt(r.createdAt());
                dtos.add(d);
            }
            ctx.json(dtos);
        } catch (Exception ex) {
            ctx.status(500).json(Map.of("error", "Database error"));
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/player/{uuid}/balance
    // Body: { "amount": <long>, "note": "<string>" }
    // -------------------------------------------------------------------------

    /**
     * Sets the player's balance to the supplied amount.
     */
    public void setBalance(Context ctx) {
        UUID uuid = parseUuidOrBadRequest(ctx);
        if (uuid == null) return;

        SetBalanceBody body = ctx.bodyAsClass(SetBalanceBody.class);
        if (body == null || body.note == null) {
            ctx.status(400).json(Map.of("error", "Missing required fields: amount, note"));
            return;
        }

        BalanceManager.BalanceResult result = balanceManager.setBalance(uuid, body.amount, body.note);
        ctx.json(Map.of("result", result.name()));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/player/{uuid}/balance/add
    // Body: { "amount": <long>, "note": "<string>" }
    // -------------------------------------------------------------------------

    /**
     * Adds the supplied amount to the player's balance.
     */
    public void addBalance(Context ctx) {
        UUID uuid = parseUuidOrBadRequest(ctx);
        if (uuid == null) return;

        SetBalanceBody body = ctx.bodyAsClass(SetBalanceBody.class);
        if (body == null || body.note == null) {
            ctx.status(400).json(Map.of("error", "Missing required fields: amount, note"));
            return;
        }

        BalanceManager.BalanceResult result = balanceManager.addBalance(uuid, body.amount, body.note);
        ctx.json(Map.of("result", result.name()));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/player/{uuid}/balance/take
    // Body: { "amount": <long>, "note": "<string>" }
    // -------------------------------------------------------------------------

    /**
     * Deducts the supplied amount from the player's balance.
     */
    public void takeBalance(Context ctx) {
        UUID uuid = parseUuidOrBadRequest(ctx);
        if (uuid == null) return;

        SetBalanceBody body = ctx.bodyAsClass(SetBalanceBody.class);
        if (body == null || body.note == null) {
            ctx.status(400).json(Map.of("error", "Missing required fields: amount, note"));
            return;
        }

        BalanceManager.BalanceResult result = balanceManager.takeBalance(uuid, body.amount, body.note);
        ctx.json(Map.of("result", result.name()));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/transfer
    // Body: { "fromUuid": "<string>", "toUuid": "<string>", "amount": <long>, "note": "<string>" }
    // -------------------------------------------------------------------------

    /**
     * Transfers currency from one player to another.
     */
    public void transfer(Context ctx) {
        TransferBody body = ctx.bodyAsClass(TransferBody.class);
        if (body == null || body.fromUuid == null || body.toUuid == null || body.note == null) {
            ctx.status(400).json(Map.of("error", "Missing required fields: fromUuid, toUuid, amount, note"));
            return;
        }

        UUID fromUuid;
        UUID toUuid;
        try {
            fromUuid = UUID.fromString(body.fromUuid);
            toUuid = UUID.fromString(body.toUuid);
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(Map.of("error", "Invalid UUID in request body"));
            return;
        }

        // Look up usernames from the balance table
        String fromUsername = resolveUsername(fromUuid);
        String toUsername = resolveUsername(toUuid);

        BalanceManager.BalanceResult result = balanceManager.transfer(
                fromUuid, fromUsername, toUuid, toUsername, body.amount);
        ctx.json(Map.of("result", result.name()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID parseUuidOrBadRequest(Context ctx) {
        try {
            return UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(Map.of("error", "Invalid UUID"));
            return null;
        }
    }

    private String resolveUsername(UUID uuid) {
        String sql = "SELECT username FROM wb_balances WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username");
                }
            }
        } catch (SQLException ignored) {
            // fall through to default
        }
        return uuid.toString();
    }

    // -------------------------------------------------------------------------
    // Request body POJOs
    // -------------------------------------------------------------------------

    public static class SetBalanceBody {
        public long amount;
        public String note;
    }

    public static class TransferBody {
        public String fromUuid;
        public String toUuid;
        public long amount;
        public String note;
    }
}
