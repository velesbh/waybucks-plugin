package space.blockway.waybucks.velocity.api.handlers;

import io.javalin.http.Context;
import space.blockway.waybucks.shared.dto.LeaderboardEntryDto;
import space.blockway.waybucks.velocity.managers.BalanceManager;

import java.util.List;

/**
 * REST handler for the balance leaderboard endpoint.
 */
public class WaybucksLeaderboardHandler {

    private final BalanceManager balanceManager;

    public WaybucksLeaderboardHandler(BalanceManager balanceManager) {
        this.balanceManager = balanceManager;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/leaderboard?limit=10
    // -------------------------------------------------------------------------

    /**
     * Returns the top-N players ordered by balance descending.
     *
     * <p>The {@code limit} query parameter controls how many entries are returned (default: 10,
     * maximum: 100).</p>
     */
    public void getLeaderboard(Context ctx) {
        int limit = 10;
        String limitParam = ctx.queryParam("limit");
        if (limitParam != null) {
            try {
                limit = Integer.parseInt(limitParam);
                if (limit < 1) limit = 1;
                if (limit > 100) limit = 100;
            } catch (NumberFormatException ignored) {
                // fall back to default
            }
        }

        List<LeaderboardEntryDto> entries = balanceManager.getLeaderboard(limit);
        ctx.json(entries);
    }
}
