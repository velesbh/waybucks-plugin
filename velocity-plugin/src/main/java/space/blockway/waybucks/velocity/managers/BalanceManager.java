package space.blockway.waybucks.velocity.managers;

import org.slf4j.Logger;
import space.blockway.waybucks.shared.dto.LeaderboardEntryDto;
import space.blockway.waybucks.velocity.config.WaybucksVelocityConfig;
import space.blockway.waybucks.velocity.database.BalanceRepository;
import space.blockway.waybucks.velocity.database.TransactionRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BalanceManager {

    public enum BalanceResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        PLAYER_NOT_FOUND,
        ABOVE_MAX,
        INVALID_AMOUNT
    }

    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final WaybucksVelocityConfig config;
    private final Logger logger;

    public BalanceManager(
            BalanceRepository balanceRepository,
            TransactionRepository transactionRepository,
            WaybucksVelocityConfig config,
            Logger logger
    ) {
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.config = config;
        this.logger = logger;
    }

    public void ensurePlayer(UUID uuid, String username) {
        try {
            balanceRepository.upsertPlayer(uuid, username);
        } catch (SQLException e) {
            logger.error("Failed to upsert player {} ({})", username, uuid, e);
        }
    }

    public long getBalance(UUID uuid) {
        try {
            return balanceRepository.getBalance(uuid);
        } catch (SQLException e) {
            logger.error("Failed to get balance for {}", uuid, e);
            return 0L;
        }
    }

    public BalanceResult setBalance(UUID uuid, long amount, String adminName) {
        if (amount < 0) {
            return BalanceResult.INVALID_AMOUNT;
        }
        if (amount > config.getMaxBalance()) {
            return BalanceResult.ABOVE_MAX;
        }

        try {
            long previous = balanceRepository.getBalance(uuid);
            balanceRepository.setBalance(uuid, amount);
            transactionRepository.record("SET", uuid, null, amount,
                    "Set by " + adminName + " (was " + previous + ")");
            return BalanceResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to set balance for {}", uuid, e);
            return BalanceResult.PLAYER_NOT_FOUND;
        }
    }

    public BalanceResult addBalance(UUID uuid, long amount, String note) {
        if (amount <= 0) {
            return BalanceResult.INVALID_AMOUNT;
        }

        try {
            long current = balanceRepository.getBalance(uuid);
            long newBalance = current + amount;
            if (newBalance > config.getMaxBalance()) {
                return BalanceResult.ABOVE_MAX;
            }

            balanceRepository.addBalance(uuid, amount);
            transactionRepository.record("ADD", uuid, null, amount, note);
            return BalanceResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to add balance for {}", uuid, e);
            return BalanceResult.PLAYER_NOT_FOUND;
        }
    }

    public BalanceResult takeBalance(UUID uuid, long amount, String note) {
        if (amount <= 0) {
            return BalanceResult.INVALID_AMOUNT;
        }

        try {
            boolean success = balanceRepository.takeBalance(uuid, amount);
            if (!success) {
                return BalanceResult.INSUFFICIENT_FUNDS;
            }
            transactionRepository.record("TAKE", uuid, null, amount, note);
            return BalanceResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to take balance from {}", uuid, e);
            return BalanceResult.PLAYER_NOT_FOUND;
        }
    }

    public BalanceResult transfer(UUID from, String fromName, UUID to, String toName, long amount) {
        if (amount <= 0) {
            return BalanceResult.INVALID_AMOUNT;
        }

        try {
            long senderBalance = balanceRepository.getBalance(from);
            if (senderBalance < amount) {
                return BalanceResult.INSUFFICIENT_FUNDS;
            }

            boolean success = balanceRepository.transfer(from, to, amount);
            if (!success) {
                return BalanceResult.INSUFFICIENT_FUNDS;
            }

            String note = fromName + " -> " + toName;
            transactionRepository.record("TRANSFER", from, to, amount, note);
            return BalanceResult.SUCCESS;
        } catch (SQLException e) {
            logger.error("Failed to transfer {} from {} to {}", amount, from, to, e);
            return BalanceResult.PLAYER_NOT_FOUND;
        }
    }

    public List<LeaderboardEntryDto> getLeaderboard(int limit) {
        try {
            List<BalanceRepository.BalanceRecord> records = balanceRepository.getTopBalances(limit);
            List<LeaderboardEntryDto> entries = new ArrayList<>(records.size());
            int rank = 1;
            for (BalanceRepository.BalanceRecord record : records) {
                entries.add(new LeaderboardEntryDto(rank++, record.uuid().toString(), record.username(), record.balance()));
            }
            return entries;
        } catch (SQLException e) {
            logger.error("Failed to fetch leaderboard", e);
            return List.of();
        }
    }
}
